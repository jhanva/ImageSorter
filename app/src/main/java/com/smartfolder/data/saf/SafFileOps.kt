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
                MoveResult.Moved(movedUri)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
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
