package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.data.saf.MoveResult
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.data.storage.DirectFileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject

/**
 * Reverts moved files back to their original folder.
 */
class UndoMoveUseCase @Inject constructor(
    private val safFileOps: SafFileOps,
    private val directFileOps: DirectFileOps
) {
    data class UndoEntry(
        val entry: MoveImagesUseCase.MovedEntry,
        val originalFolderUri: Uri
    )

    data class UndoReport(
        val restored: Int,
        val failed: Int,
        val errors: List<String>,
        val restoredUris: Map<Long, Uri> = emptyMap()
    )

    suspend operator fun invoke(entries: List<UndoEntry>): UndoReport = withContext(Dispatchers.IO) {
        var restored = 0
        var failed = 0
        val errors = mutableListOf<String>()
        val restoredUris = mutableMapOf<Long, Uri>()

        for (undo in entries) {
            yield()

            val originalDir = directFileOps.resolveFile(undo.originalFolderUri)
            val movedFile = directFileOps.resolveFile(undo.entry.newUri)
            val result = if (originalDir != null && movedFile != null && movedFile.exists()) {
                directFileOps.moveFile(movedFile, originalDir, undo.entry.image.displayName)
            } else {
                safFileOps.moveFile(
                    sourceUri = undo.entry.newUri,
                    destinationFolderUri = undo.originalFolderUri,
                    displayName = undo.entry.image.displayName
                )
            }

            when (result) {
                is MoveResult.Moved -> {
                    restored++
                    restoredUris[undo.entry.image.id] = result.newUri
                }
                is MoveResult.CopiedOnly -> {
                    restored++
                    restoredUris[undo.entry.image.id] = result.newUri
                    errors.add("${undo.entry.image.displayName}: restored as copy (${result.reason})")
                }
                is MoveResult.Failure -> {
                    failed++
                    errors.add("${undo.entry.image.displayName}: ${result.error}")
                }
            }
        }

        UndoReport(
            restored = restored,
            failed = failed,
            errors = errors,
            restoredUris = restoredUris
        )
    }
}
