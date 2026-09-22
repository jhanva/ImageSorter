package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.data.saf.MoveResult
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.domain.model.ImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Stages a deletion: the image is moved into a trash folder inside the source
 * tree instead of being destroyed, so the action stays undoable. The user
 * empties the trash folder from the trash screen whenever they want.
 */
class MoveToTrashUseCase @Inject constructor(
    private val safFileOps: SafFileOps
) {
    /**
     * [warning] is set when the image was copied into the trash but the
     * original could not be removed, so the caller can tell the user instead of
     * reporting a clean delete while the file is still in the source folder.
     */
    data class TrashOutcome(
        val entry: MoveImagesUseCase.MovedEntry,
        val warning: String? = null
    )

    suspend operator fun invoke(
        image: ImageInfo,
        sourceFolderUri: Uri
    ): Result<TrashOutcome> = withContext(Dispatchers.IO) {
        when (val result = safFileOps.moveFileToChildFolder(
            sourceUri = image.uri,
            treeUri = sourceFolderUri,
            childFolderName = SafFileOps.TRASH_FOLDER_NAME,
            displayName = image.displayName
        )) {
            is MoveResult.Moved ->
                Result.success(
                    TrashOutcome(MoveImagesUseCase.MovedEntry(image, result.newUri))
                )
            is MoveResult.CopiedOnly ->
                Result.success(
                    TrashOutcome(
                        entry = MoveImagesUseCase.MovedEntry(image, result.newUri),
                        warning = "${image.displayName}: copied to the trash folder, " +
                            "but the original is still in the source folder (${result.reason})"
                    )
                )
            is MoveResult.Failure ->
                Result.failure(IllegalStateException("${image.displayName}: ${result.error}"))
        }
    }
}
