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
 * empties the trash folder from a file manager whenever they want.
 */
class MoveToTrashUseCase @Inject constructor(
    private val safFileOps: SafFileOps
) {
    suspend operator fun invoke(
        image: ImageInfo,
        sourceFolderUri: Uri
    ): Result<MoveImagesUseCase.MovedEntry> = withContext(Dispatchers.IO) {
        when (val result = safFileOps.moveFileToChildFolder(
            sourceUri = image.uri,
            treeUri = sourceFolderUri,
            childFolderName = SafFileOps.TRASH_FOLDER_NAME,
            displayName = image.displayName
        )) {
            is MoveResult.Moved ->
                Result.success(MoveImagesUseCase.MovedEntry(image, result.newUri))
            is MoveResult.CopiedOnly ->
                Result.success(MoveImagesUseCase.MovedEntry(image, result.newUri))
            is MoveResult.Failure ->
                Result.failure(IllegalStateException("${image.displayName}: ${result.error}"))
        }
    }
}
