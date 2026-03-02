package com.smartfolder.data.saf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafFileOps @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun moveFile(sourceUri: Uri, destinationFolderUri: Uri, displayName: String, mimeType: String): MoveResult {
        val destFolder = DocumentFile.fromTreeUri(context, destinationFolderUri)
            ?: return MoveResult.Failure("Cannot access destination folder")

        val destFile = destFolder.createFile(mimeType, displayName)
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
}
