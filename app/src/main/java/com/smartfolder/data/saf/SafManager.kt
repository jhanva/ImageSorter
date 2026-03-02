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
    fun listImageFiles(treeUri: Uri): List<SafImageFile> {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val results = mutableListOf<SafImageFile>()

        try {
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
                    val name = cursor.getString(nameCol) ?: continue
                    val mimeType = cursor.getString(mimeCol) ?: continue

                    // Filter by image mime type (single source of truth)
                    if (mimeType !in imageMimeTypes) continue

                    val docId = cursor.getString(idCol)
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    results.add(
                        SafImageFile(
                            uri = fileUri,
                            displayName = name,
                            sizeBytes = cursor.getLong(sizeCol),
                            lastModified = cursor.getLong(modifiedCol),
                            mimeType = mimeType
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Fallback: return empty list on query failure
            return emptyList()
        }

        return results
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
