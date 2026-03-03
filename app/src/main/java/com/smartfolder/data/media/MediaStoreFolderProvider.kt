package com.smartfolder.data.media

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.smartfolder.domain.model.ImageFolderOption

@Singleton
class MediaStoreFolderProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getImageCountForDocumentId(documentId: String): Int {
        return getImageFolders()
            .firstOrNull { it.documentId == documentId }
            ?.imageCount
            ?: 0
    }

    fun getImageFolders(): List<ImageFolderOption> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA
        )

        val countsByDocId = LinkedHashMap<String, MutableFolder>()
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val relativeCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val relativePath = if (relativeCol >= 0) cursor.getString(relativeCol) else null
                val absolutePath = if (dataCol >= 0) cursor.getString(dataCol) else null
                val documentId = buildDocumentId(relativePath, absolutePath) ?: continue
                val bucketName = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                val displayName = bucketName?.takeIf { it.isNotBlank() }
                    ?: documentId.substringAfter(':').substringAfterLast('/').ifBlank { "Internal storage" }

                val entry = countsByDocId.getOrPut(documentId) {
                    MutableFolder(displayName = displayName, imageCount = 0)
                }
                entry.imageCount += 1
            }
        }

        return countsByDocId.entries
            .map { (docId, folder) ->
                ImageFolderOption(
                    displayName = folder.displayName,
                    documentId = docId,
                    imageCount = folder.imageCount
                )
            }
            .sortedWith(compareByDescending<ImageFolderOption> { it.imageCount }.thenBy { it.displayName.lowercase() })
    }

    private fun buildDocumentId(relativePath: String?, absolutePath: String?): String? {
        val fromRelative = relativePath
            ?.trim()
            ?.trim('/')
            ?.let { if (it.isBlank()) "primary:" else "primary:$it" }
        if (fromRelative != null) return fromRelative

        val normalizedAbsolute = absolutePath
            ?.replace('\\', '/')
            ?.substringBeforeLast('/', "")
            ?: return null
        val prefix = "/storage/emulated/0/"
        if (!normalizedAbsolute.startsWith(prefix)) return null
        val relative = normalizedAbsolute.removePrefix(prefix).trim('/')
        return if (relative.isBlank()) "primary:" else "primary:$relative"
    }

    private data class MutableFolder(
        val displayName: String,
        var imageCount: Int
    )
}
