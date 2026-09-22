package com.smartfolder.data.saf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafFileOps @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TRASH_FOLDER_NAME = "ImageSorterTrash"

        private val TRASH_FOLDER_REGEX =
            Regex("^" + Regex.escape(TRASH_FOLDER_NAME) + "( \\(\\d+\\))?$")

        /**
         * A provider does not fail when a folder name is taken: it creates a
         * numbered sibling ("ImageSorterTrash (1)") instead. Those siblings are
         * trash folders too, so both the trash screen and the triage queue have
         * to recognise them by pattern and not by exact name.
         */
        fun isTrashFolderName(name: String): Boolean = TRASH_FOLDER_REGEX.matches(name)
    }

    /**
     * Moves a file into a direct child folder of the given tree (creating the
     * folder if needed). Used for the staging trash inside the source folder.
     */
    private val childFolderCache = mutableMapOf<Pair<Uri, String>, Uri>()

    fun moveFileToChildFolder(
        sourceUri: Uri,
        treeUri: Uri,
        childFolderName: String,
        displayName: String
    ): MoveResult {
        // Resolving the child folder scans the whole parent; cache it so only
        // the first delete of a session pays that cost.
        val cacheKey = treeUri to childFolderName
        val childUri = childFolderCache[cacheKey]
            ?: findOrCreateChildFolder(treeUri, childFolderName)?.also {
                childFolderCache[cacheKey] = it
            }
            ?: return MoveResult.Failure("Cannot create folder $childFolderName")

        // Fast path: same-provider move.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            sourceUri.authority == childUri.authority
        ) {
            try {
                val sourceDocId = DocumentsContract.getDocumentId(sourceUri)
                val sourceParentDocId = sourceDocId
                    .substringBeforeLast('/', missingDelimiterValue = "")
                    .ifBlank { DocumentsContract.getTreeDocumentId(sourceUri) }
                val sourceParentUri =
                    DocumentsContract.buildDocumentUriUsingTree(sourceUri, sourceParentDocId)
                val movedUri = DocumentsContract.moveDocument(
                    context.contentResolver,
                    sourceUri,
                    sourceParentUri,
                    childUri
                )
                if (movedUri != null) return MoveResult.Moved(treeQualified(treeUri, movedUri))
            } catch (_: Exception) {
                // Fall through to copy + delete.
            }
        }

        return copyThenDeleteIntoUri(sourceUri, childUri, displayName)
    }

    fun deleteDocument(uri: Uri): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Every trash folder directly under the tree, including the numbered
     * duplicates a provider may have created in earlier sessions, so staged
     * images are never left stranded in a folder nothing reads.
     */
    fun findTrashFolders(treeUri: Uri): List<ChildFolder> =
        listChildFolders(treeUri).filter { isTrashFolderName(it.displayName) }

    /**
     * Resolves a direct child folder with ONE children query instead of
     * DocumentFile.findFile, which issues a query per child and freezes the
     * UI on folders with thousands of files.
     */
    private fun findOrCreateChildFolder(treeUri: Uri, childFolderName: String): Uri? {
        runCatching { listChildFolders(treeUri) }.getOrNull()
            ?.firstOrNull { it.displayName == childFolderName }
            ?.let { return it.uri }

        val created = try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            DocumentsContract.createDocument(
                context.contentResolver,
                rootUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                childFolderName
            )
        } catch (_: Exception) {
            null
        } ?: return null

        // createDocument does not fail on a name clash: it silently creates
        // "<name> (1)". That duplicate would collect the deleted images while
        // the trash screen kept reading the original folder, so drop the empty
        // duplicate and reuse the folder that was already there.
        val createdName = queryDisplayName(created)
        if (createdName != null && createdName != childFolderName) {
            val original = runCatching { listChildFolders(treeUri) }.getOrNull()
                ?.firstOrNull { it.displayName == childFolderName }
            if (original != null) {
                deleteDocument(created)
                return original.uri
            }
        }
        return created
    }

    private fun queryDisplayName(documentUri: Uri): String? {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return try {
            context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lists the direct child folders of the tree with a single children query.
     * Throws instead of returning an empty list so a permission or provider
     * failure is reported to the user rather than looking like an empty folder.
     */
    private fun listChildFolders(treeUri: Uri): List<ChildFolder> {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: throw SafAccessException("Cannot resolve the source folder")
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val folders = mutableListOf<ChildFolder>()
        try {
            val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
                ?: throw SafAccessException("The source folder could not be read")
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext()) {
                    if (it.getString(mimeCol) != DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val name = it.getString(nameCol) ?: continue
                    val docId = it.getString(idCol) ?: continue
                    folders.add(
                        ChildFolder(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                            displayName = name,
                            relativePath = docId.substringAfter(':', docId)
                        )
                    )
                }
            }
        } catch (e: SafAccessException) {
            throw e
        } catch (e: Exception) {
            throw SafAccessException(e.message ?: "The source folder could not be read", e)
        }
        return folders
    }

    private fun copyThenDeleteIntoUri(
        sourceUri: Uri,
        destFolderUri: Uri,
        displayName: String
    ): MoveResult {
        val destFile = try {
            DocumentsContract.createDocument(
                context.contentResolver,
                destFolderUri,
                resolveMimeType(sourceUri, displayName),
                displayName
            )
        } catch (_: Exception) {
            null
        } ?: return MoveResult.Failure("Cannot create file in destination folder")

        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destFile)?.use { output ->
                    input.copyTo(output)
                }
            } ?: return MoveResult.Failure("Cannot read source file")

            val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)
            if (sourceDoc?.delete() == true) {
                MoveResult.Moved(destFile)
            } else {
                MoveResult.CopiedOnly(destFile, "Could not delete original file")
            }
        } catch (e: Exception) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, destFile)
            } catch (ignored: Exception) {
                // Best effort cleanup
            }
            MoveResult.Failure(e.message ?: "Unknown error during file move")
        }
    }

    fun moveFile(sourceUri: Uri, destinationFolderUri: Uri, displayName: String): MoveResult {
        // Primary path for modern Android/shared storage.
        tryMoveWithMediaStore(sourceUri, destinationFolderUri)?.let { return it }

        // Fast-path: true SAF move within a provider when supported.
        tryMoveWithDocumentsContract(sourceUri, destinationFolderUri)?.let { return it }

        val destFolder = DocumentFile.fromTreeUri(context, destinationFolderUri)
            ?: return MoveResult.Failure("Cannot access destination folder")

        val resolvedDisplayName = DestinationNameResolver.resolveUniqueDisplayName(
            existingDisplayNames = destFolder.listFiles().mapNotNull { it.name }.toSet(),
            requestedDisplayName = displayName
        )
        val resolvedMimeType = resolveMimeType(sourceUri, resolvedDisplayName)
        val destFile = destFolder.createFile(resolvedMimeType, resolvedDisplayName)
            ?: return MoveResult.Failure("Cannot create file in destination folder")

        return try {
            // Copy content
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            } ?: return MoveResult.Failure("Cannot read source file")

            // Try to delete original
            val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)
            if (sourceDoc?.delete() == true) {
                MoveResult.Moved(destFile.uri)
            } else {
                MoveResult.CopiedOnly(destFile.uri, "Could not delete original file")
            }
        } catch (e: Exception) {
            // Clean up destination file on failure
            try {
                destFile.delete()
            } catch (ignored: Exception) {
                // Best effort cleanup
            }
            MoveResult.Failure(e.message ?: "Unknown error during file move")
        }
    }

    private fun tryMoveWithMediaStore(
        sourceUri: Uri,
        destinationFolderUri: Uri
    ): MoveResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (sourceUri.scheme != "content") return null
        if (!sourceUri.authority.orEmpty().contains("media")) return null

        val destinationDocId = runCatching {
            DocumentsContract.getTreeDocumentId(destinationFolderUri)
        }.getOrNull() ?: return null

        val relativePath = destinationDocId.substringAfter(':', "").trim('/')
        val normalizedRelativePath = if (relativePath.isBlank()) "" else "$relativePath/"

        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedRelativePath)
            }
            val updated = context.contentResolver.update(sourceUri, values, null, null)
            if (updated > 0) MoveResult.Moved(sourceUri) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryMoveWithDocumentsContract(
        sourceUri: Uri,
        destinationFolderUri: Uri
    ): MoveResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        if (sourceUri.authority != destinationFolderUri.authority) return null

        return try {
            val resolver = context.contentResolver
            val sourceDocId = DocumentsContract.getDocumentId(sourceUri)
            val sourceParentDocId = sourceDocId.substringBeforeLast('/', missingDelimiterValue = "")
                .ifBlank { DocumentsContract.getTreeDocumentId(sourceUri) }
            val sourceParentUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, sourceParentDocId)
            val targetParentDocId = DocumentsContract.getTreeDocumentId(destinationFolderUri)
            val targetParentUri = DocumentsContract.buildDocumentUriUsingTree(destinationFolderUri, targetParentDocId)

            val movedUri = DocumentsContract.moveDocument(
                resolver,
                sourceUri,
                sourceParentUri,
                targetParentUri
            )
            if (movedUri != null) {
                MoveResult.Moved(treeQualified(destinationFolderUri, movedUri))
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Re-qualifies a moved document URI against the granted tree it now lives
     * under. DocumentsContract.moveDocument (and some providers) return a bare
     * document URI (content://authority/document/...) that is NOT covered by
     * the tree permission grant, so a later read or reverse move throws
     * SecurityException. Building a tree-scoped URI keeps it accessible; if the
     * URI cannot be re-qualified we fall back to the original.
     */
    private fun treeQualified(treeUri: Uri, movedUri: Uri): Uri {
        return runCatching {
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getDocumentId(movedUri)
            )
        }.getOrDefault(movedUri)
    }

    private fun resolveMimeType(sourceUri: Uri, displayName: String): String {
        val fromResolver = context.contentResolver.getType(sourceUri)
        if (!fromResolver.isNullOrBlank() && fromResolver.startsWith("image/")) {
            return fromResolver
        }
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heif", "heic" -> "image/heif"
            else -> "image/*"
        }
    }
}

internal object DestinationNameResolver {
    fun resolveUniqueDisplayName(
        existingDisplayNames: Set<String>,
        requestedDisplayName: String
    ): String {
        if (requestedDisplayName !in existingDisplayNames) {
            return requestedDisplayName
        }

        val extension = requestedDisplayName.substringAfterLast('.', "")
            .takeIf { requestedDisplayName.contains('.') }
        val baseName = if (extension == null) {
            requestedDisplayName
        } else {
            requestedDisplayName.removeSuffix(".$extension")
        }

        var index = 1
        while (true) {
            val candidate = if (extension == null) {
                "$baseName ($index)"
            } else {
                "$baseName ($index).$extension"
            }
            if (candidate !in existingDisplayNames) {
                return candidate
            }
            index++
        }
    }
}

/**
 * A direct child folder of a granted tree. [relativePath] is the provider
 * document id without its volume prefix ("DCIM/Camera/ImageSorterTrash"), which
 * is what the user sees in a file manager.
 */
data class ChildFolder(
    val uri: Uri,
    val displayName: String,
    val relativePath: String
)
