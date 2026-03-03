package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.data.saf.MoveResult
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.TransactionRunner
import kotlinx.coroutines.yield
import javax.inject.Inject

class MoveImagesUseCase @Inject constructor(
    private val safFileOps: SafFileOps,
    private val imageRepository: ImageRepository,
    private val transactionRunner: TransactionRunner
) {
    data class MoveReport(
        val moved: Int,
        val copiedOnly: Int,
        val failed: Int,
        val errors: List<String>,
        val movedImageIds: Set<Long>
    )

    suspend operator fun invoke(
        images: List<ImageInfo>,
        destinationFolderUri: Uri
    ): MoveReport {
        var moved = 0
        var copiedOnly = 0
        var failed = 0
        val errors = mutableListOf<String>()
        val movedImageIds = mutableSetOf<Long>()

        for (image in images) {
            yield()

            val result = safFileOps.moveFile(
                sourceUri = image.uri,
                destinationFolderUri = destinationFolderUri,
                displayName = image.displayName
            )

            when (result) {
                is MoveResult.Moved -> {
                    try {
                        transactionRunner.runInTransaction {
                            imageRepository.delete(image)
                        }
                        moved++
                        movedImageIds.add(image.id)
                    } catch (e: Exception) {
                        // File was moved but DB delete failed; count as moved
                        // but log error so user knows DB is inconsistent
                        moved++
                        movedImageIds.add(image.id)
                        errors.add("${image.displayName}: moved but DB cleanup failed")
                    }
                }
                is MoveResult.CopiedOnly -> {
                    copiedOnly++
                }
                is MoveResult.Failure -> {
                    failed++
                    errors.add("${image.displayName}: ${result.error}")
                }
            }
        }

        return MoveReport(
            moved = moved,
            copiedOnly = copiedOnly,
            failed = failed,
            errors = errors,
            movedImageIds = movedImageIds
        )
    }
}
