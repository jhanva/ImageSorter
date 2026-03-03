package com.smartfolder.data.media

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.smartfolder.data.saf.SafImageFile
import dagger.hilt.android.qualifiers.ApplicationContext
import com.smartfolder.domain.model.ImageFolderOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreFolderProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun listImageFilesForDocumentId(documentId: String, recursive: Boolean = true): List<SafImageFile> {
        val relativePath = documentId.substringAfter(':', "").trim('/')
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA
        )

        val (selection, selectionArgs) = buildMediaStoreSelection(relativePath, recursive)
        val results = mutableListOf<SafImageFile>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
            val modifiedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: "image_$id" else "image_$id"
                val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/*" else "image/*"
                val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                val modifiedSeconds = if (modifiedCol >= 0) cursor.getLong(modifiedCol) else 0L
                val lastModified = modifiedSeconds * 1000L

                results.add(
                    SafImageFile(
                        uri = uri,
                        displayName = displayName,
                        sizeBytes = sizeBytes,
                        lastModified = lastModified,
                        mimeType = mimeType
                    )
                )
            }
        }

        return results
    }

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

    private fun buildMediaStoreSelection(
        relativePath: String,
        recursive: Boolean
    ): Pair<String?, Array<String>?> {
        if (relativePath.isBlank()) return null to null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val normalized = "$relativePath/"
            return if (recursive) {
                "( ${MediaStore.Images.Media.RELATIVE_PATH} = ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? )" to
                        arrayOf(normalized, "$normalized%")
            } else {
                "${MediaStore.Images.Media.RELATIVE_PATH} = ?" to arrayOf(normalized)
            }
        }

        val absolute = "/storage/emulated/0/$relativePath"
        return "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("$absolute/%")
    }

    private data class MutableFolder(
        val displayName: String,
        var imageCount: Int
    )
}
