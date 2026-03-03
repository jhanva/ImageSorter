package com.smartfolder.data.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val imageMimeTypes = setOf(
        "image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif", "image/heif", "image/heic"
    )
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "heif", "heic"
    )

    fun takePersistablePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // Try read-only if read-write fails
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: SecurityException) {
                // Permission could not be persisted
            }
        }
    }

    fun hasPersistedPermission(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    /**
     * Lists image files using a single ContentResolver query instead of
     * DocumentFile.listFiles() which makes individual queries per file.
     * This is 10-100x faster for folders with thousands of files.
     */
    fun listImageFiles(treeUri: Uri, recursive: Boolean = true): List<SafImageFile> {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val results = mutableListOf<SafImageFile>()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        queue.add(rootDocId)

        try {
            while (queue.isNotEmpty()) {
                val parentDocId = queue.removeFirst()
                if (!visited.add(parentDocId)) continue

                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

                context.contentResolver.query(
                    childrenUri, projection, null, null, null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    )
                    val nameCol = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )
                    val mimeCol = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    )
                    val sizeCol = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_SIZE
                    )
                    val modifiedCol = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    )

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val mimeType = cursor.getString(mimeCol)

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (recursive) queue.add(docId)
                            continue
                        }

                        if (!isImageCandidate(name, mimeType)) continue

                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        results.add(
                            SafImageFile(
                                uri = fileUri,
                                displayName = name,
                                sizeBytes = cursor.getLong(sizeCol),
                                lastModified = cursor.getLong(modifiedCol),
                                mimeType = mimeType ?: ""
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to list images from selected folder. Please verify folder access and try again.",
                e
            )
        }

        return results
    }

    private fun isImageCandidate(displayName: String, mimeType: String?): Boolean {
        if (mimeType != null) {
            if (mimeType in imageMimeTypes) return true
            if (mimeType.startsWith("image/")) return true
            if (mimeType == "application/octet-stream") {
                return hasImageExtension(displayName)
            }
        }
        return hasImageExtension(displayName)
    }

    private fun hasImageExtension(displayName: String): Boolean {
        val dotIndex = displayName.lastIndexOf('.')
        if (dotIndex == -1 || dotIndex == displayName.length - 1) return false
        val ext = displayName.substring(dotIndex + 1).lowercase()
        return ext in imageExtensions
    }

    fun getFolderDisplayName(treeUri: Uri): String {
        val documentFile = DocumentFile.fromTreeUri(context, treeUri)
        return documentFile?.name ?: treeUri.lastPathSegment ?: "Unknown"
    }

    fun getDocumentFile(uri: Uri): DocumentFile? {
        return DocumentFile.fromSingleUri(context, uri)
    }

    fun getTreeDocumentFile(uri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(context, uri)
    }
}
