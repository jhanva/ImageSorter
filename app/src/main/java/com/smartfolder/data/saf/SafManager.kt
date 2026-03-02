package com.smartfolder.data.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

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

    fun listImageFiles(treeUri: Uri): List<SafImageFile> {
        val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return documentFile.listFiles()
            .filter { file ->
                file.isFile && file.name?.let { name ->
                    val ext = name.substringAfterLast('.', "").lowercase()
                    ext in imageExtensions
                } ?: false
            }
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                val uri = file.uri
                SafImageFile(
                    uri = uri,
                    displayName = name,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    mimeType = file.type ?: "image/*"
                )
            }
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
