package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.data.saf.MoveResult
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.repository.ImageRepository
import javax.inject.Inject

class MoveImagesUseCase @Inject constructor(
    private val safFileOps: SafFileOps,
    private val imageRepository: ImageRepository
) {
    data class MoveReport(
        val moved: Int,
        val copiedOnly: Int,
        val failed: Int,
        val errors: List<String>
    )

    suspend operator fun invoke(
        images: List<ImageInfo>,
        destinationFolderUri: Uri
    ): MoveReport {
        var moved = 0
        var copiedOnly = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for (image in images) {
            val result = safFileOps.moveFile(
                sourceUri = image.uri,
                destinationFolderUri = destinationFolderUri,
                displayName = image.displayName,
                mimeType = "image/*"
            )

            when (result) {
                is MoveResult.Moved -> {
                    moved++
                    imageRepository.delete(image)
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

        return MoveReport(moved = moved, copiedOnly = copiedOnly, failed = failed, errors = errors)
    }
}
