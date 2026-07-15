package com.smartfolder.domain.usecase

import com.smartfolder.data.saf.SafFileOps
import com.smartfolder.domain.model.ImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeleteTrashImageUseCase @Inject constructor(
    private val safFileOps: SafFileOps
) {
    suspend operator fun invoke(image: ImageInfo): Result<Unit> = withContext(Dispatchers.IO) {
        if (safFileOps.deleteDocument(image.uri)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Could not delete ${image.displayName}"))
        }
    }
}
