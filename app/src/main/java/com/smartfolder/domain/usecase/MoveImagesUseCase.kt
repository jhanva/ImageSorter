package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.data.saf.MoveResult
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.domain.model.ImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject

class MoveImagesUseCase @Inject constructor(
    private val safFileOps: SafFileOps
) {
    data class MovedEntry(
        val image: ImageInfo,
        val newUri: Uri
    )

    data class MoveReport(
        val moved: Int,
        val copiedOnly: Int,
        val failed: Int,
        val errors: List<String>,
        val movedImageIds: Set<Long>,
        val movedEntries: List<MovedEntry> = emptyList()
    )

    suspend operator fun invoke(
        images: List<ImageInfo>,
        destinationFolderUri: Uri
    ): MoveReport = withContext(Dispatchers.IO) {
        var moved = 0
        var copiedOnly = 0
        var failed = 0
        val errors = mutableListOf<String>()
        val movedImageIds = mutableSetOf<Long>()
        val movedEntries = mutableListOf<MovedEntry>()

        for (image in images) {
            yield()

            val result = safFileOps.moveFile(
                sourceUri = image.uri,
                destinationFolderUri = destinationFolderUri,
                displayName = image.displayName
            )

            when (result) {
                is MoveResult.Moved -> {
                    moved++
                    movedImageIds.add(image.id)
                    movedEntries.add(MovedEntry(image = image, newUri = result.newUri))
                }
                is MoveResult.CopiedOnly -> {
                    copiedOnly++
                    errors.add("${image.displayName}: copied only (${result.reason})")
                }
                is MoveResult.Failure -> {
                    failed++
                    errors.add("${image.displayName}: ${result.error}")
                }
            }
        }

        MoveReport(
            moved = moved,
            copiedOnly = copiedOnly,
            failed = failed,
            errors = errors,
            movedImageIds = movedImageIds,
            movedEntries = movedEntries
        )
    }
}
