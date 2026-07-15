package com.smartfolder.domain.usecase

import com.smartfolder.data.saf.MoveResult
import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RestoreFromTrashUseCase @Inject constructor(
    private val safFileOps: SafFileOps
) {
    suspend operator fun invoke(image: ImageInfo, sourceFolder: Folder): Result<Unit> =
        withContext(Dispatchers.IO) {
            when (val result = safFileOps.moveFile(image.uri, sourceFolder.uri, image.displayName)) {
                is MoveResult.Moved -> Result.success(Unit)
                is MoveResult.CopiedOnly -> Result.success(Unit)
                is MoveResult.Failure ->
                    Result.failure(IllegalStateException("${image.displayName}: ${result.error}"))
            }
        }
}
