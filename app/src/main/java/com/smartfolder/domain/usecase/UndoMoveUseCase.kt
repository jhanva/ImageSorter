package com.smartfolder.domain.usecase

import com.smartfolder.data.saf.MoveResult
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.repository.TransactionRunner
import kotlinx.coroutines.yield
import javax.inject.Inject

/**
 * Reverts the last move batch: files are moved back to their original folder
 * and the image/suggestion rows removed during the move are reinserted, so the
 * review screen shows them again.
 */
class UndoMoveUseCase @Inject constructor(
    private val safFileOps: SafFileOps,
    private val imageRepository: ImageRepository,
    private val folderRepository: FolderRepository,
    private val suggestionRepository: SuggestionRepository,
    private val transactionRunner: TransactionRunner
) {
    data class UndoBatch(
        val entries: List<MoveImagesUseCase.MovedEntry>,
        val suggestions: List<StoredSuggestion>
    )

    data class UndoReport(
        val restored: Int,
        val failed: Int,
        val errors: List<String>
    )

    suspend operator fun invoke(batch: UndoBatch): UndoReport {
        var restored = 0
        var failed = 0
        val errors = mutableListOf<String>()
        val restoredImages = mutableListOf<com.smartfolder.domain.model.ImageInfo>()
        val restoredImageIds = mutableSetOf<Long>()

        for (entry in batch.entries) {
            yield()

            val originalFolder = folderRepository.getById(entry.image.folderId)
            if (originalFolder == null) {
                failed++
                errors.add("${entry.image.displayName}: original folder no longer exists")
                continue
            }

            when (val result = safFileOps.moveFile(
                sourceUri = entry.newUri,
                destinationFolderUri = originalFolder.uri,
                displayName = entry.image.displayName
            )) {
                is MoveResult.Moved -> {
                    restoredImages.add(entry.image.copy(uri = result.newUri))
                    restoredImageIds.add(entry.image.id)
                    restored++
                }
                is MoveResult.CopiedOnly -> {
                    restoredImages.add(entry.image.copy(uri = result.newUri))
                    restoredImageIds.add(entry.image.id)
                    restored++
                    errors.add("${entry.image.displayName}: restored as copy (${result.reason})")
                }
                is MoveResult.Failure -> {
                    failed++
                    errors.add("${entry.image.displayName}: ${result.error}")
                }
            }
        }

        if (restoredImages.isNotEmpty()) {
            transactionRunner.runInTransaction {
                imageRepository.insertAll(restoredImages)
                val restoredSuggestions = batch.suggestions.filter { it.imageId in restoredImageIds }
                if (restoredSuggestions.isNotEmpty()) {
                    suggestionRepository.insertAll(restoredSuggestions)
                }
            }
        }

        return UndoReport(restored = restored, failed = failed, errors = errors)
    }
}
