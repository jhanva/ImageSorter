package com.smartfolder.data.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.smartfolder.data.saf.DestinationNameResolver
import com.smartfolder.data.saf.MoveResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves images by path, which is what all files access buys: destinations no
 * longer need a SAF grant of their own, so every image folder on the device can
 * be a destination without the user picking it first.
 *
 * A rename is used whenever both sides live on the same volume; otherwise the
 * bytes are copied and the original removed. MediaStore is told about both
 * paths so the gallery does not keep showing the image in its old folder.
 */
@Singleton
class DirectFileOps @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun moveFile(sourceUri: Uri, destinationDir: String, displayName: String): MoveResult {
        val sourceFile = sourceUri.toFileOrNull()
            ?: return MoveResult.Failure("Not a file path: $sourceUri")
        return moveFile(sourceFile, File(destinationDir), displayName)
    }

    fun moveFile(sourceFile: File, destinationDir: File, displayName: String): MoveResult {
        if (!sourceFile.exists()) return MoveResult.Failure("${sourceFile.name} no longer exists")
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            return MoveResult.Failure("Cannot create ${destinationDir.name}")
        }

        val target = File(destinationDir, uniqueNameIn(destinationDir, displayName))

        if (sourceFile.renameTo(target)) {
            notifyMediaStore(sourceFile, target)
            return MoveResult.Moved(Uri.fromFile(target))
        }

        return try {
            sourceFile.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (sourceFile.delete()) {
                notifyMediaStore(sourceFile, target)
                MoveResult.Moved(Uri.fromFile(target))
            } else {
                notifyMediaStore(sourceFile, target)
                MoveResult.CopiedOnly(Uri.fromFile(target), "Could not delete the original file")
            }
        } catch (e: Exception) {
            runCatching { target.delete() }
            MoveResult.Failure(e.message ?: "Unknown error during file move")
        }
    }

    fun moveToChildFolder(
        sourceUri: Uri,
        parentDir: String,
        childFolderName: String,
        displayName: String
    ): MoveResult {
        val sourceFile = sourceUri.toFileOrNull()
            ?: return MoveResult.Failure("Not a file path: $sourceUri")
        return moveFile(sourceFile, File(parentDir, childFolderName), displayName)
    }

    fun delete(uri: Uri): Boolean {
        val file = uri.toFileOrNull() ?: return false
        val deleted = file.delete()
        if (deleted) notifyMediaStore(file, null)
        return deleted
    }

    fun listImageFiles(dir: File, recursive: Boolean): List<File> {
        if (!dir.isDirectory) return emptyList()
        val results = mutableListOf<File>()
        val pending = ArrayDeque(listOf(dir))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val children = current.listFiles() ?: continue
            for (child in children) {
                when {
                    child.isDirectory -> if (recursive) pending.addLast(child)
                    isImage(child.name) -> results.add(child)
                }
            }
        }
        return results
    }

    fun uriFor(file: File): Uri = Uri.fromFile(file)

    fun isImage(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    private fun uniqueNameIn(dir: File, displayName: String): String {
        val existing = dir.list()?.toSet().orEmpty()
        return DestinationNameResolver.resolveUniqueDisplayName(existing, displayName)
    }

    /**
     * Without this the gallery keeps the image indexed at its old path and the
     * folder listings the app reads from MediaStore go stale.
     */
    private fun notifyMediaStore(from: File, to: File?) {
        val paths = listOfNotNull(from.absolutePath, to?.absolutePath).toTypedArray()
        runCatching {
            MediaScannerConnection.scanFile(context, paths, null, null)
        }
    }

    /**
     * Resolves any uri the app handles to a real file. Sources are still listed
     * through SAF, so their uris are documents, not paths: their document id
     * ("primary:DCIM/Camera/a.jpg") is relative to this profile's storage root,
     * which is what makes a rename possible instead of a copy.
     */
    fun resolveFile(uri: Uri): File? {
        when (uri.scheme) {
            "file", null -> return uri.path?.let(::File)
            "content" -> Unit
            else -> return null
        }

        val authority = uri.authority.orEmpty()
        if (authority.contains("externalstorage")) {
            val docId = runCatching {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.getDocumentId(uri)
                } else {
                    DocumentsContract.getTreeDocumentId(uri)
                }
            }.getOrNull() ?: return null
            val volume = docId.substringBefore(':', "")
            if (volume != "primary") return null
            val relative = docId.substringAfter(':', "").trim('/')
            val root = Environment.getExternalStorageDirectory()?.absolutePath ?: return null
            return if (relative.isBlank()) File(root) else File(root, relative)
        }

        // MediaStore items expose the path in DATA.
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.let(::File) else null
            }
        }.getOrNull()
    }

    private fun Uri.toFileOrNull(): File? = resolveFile(this)

    private companion object {
        val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "bmp", "gif", "heif", "heic"
        )
    }
}
