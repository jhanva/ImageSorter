package com.smartfolder.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.usecase.IndexFolderUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.last

@HiltWorker
class IndexingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val folderRepository: FolderRepository,
    private val indexFolderUseCase: IndexFolderUseCase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_FOLDER_ID = "folder_id"
        const val KEY_MODEL_CHOICE = "model_choice"
        const val KEY_PROGRESS_PHASE = "progress_phase"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_ERROR_MESSAGE = "error_message"
    }

    override suspend fun doWork(): Result {
        val folderId = inputData.getLong(KEY_FOLDER_ID, -1)
        if (folderId == -1L) return Result.failure(
            Data.Builder().putString(KEY_ERROR_MESSAGE, "Invalid folder ID").build()
        )

        val modelName = inputData.getString(KEY_MODEL_CHOICE) ?: ModelChoice.FAST.name
        val modelChoice = try {
            ModelChoice.valueOf(modelName)
        } catch (e: IllegalArgumentException) {
            ModelChoice.FAST
        }

        val folder = folderRepository.getById(folderId) ?: return Result.failure(
            Data.Builder().putString(KEY_ERROR_MESSAGE, "Folder not found").build()
        )

        var lastProgress = IndexingPhase.IDLE

        indexFolderUseCase(folder, modelChoice).collect { progress ->
            lastProgress = progress.phase
            setProgress(
                Data.Builder()
                    .putString(KEY_PROGRESS_PHASE, progress.phase.name)
                    .putInt(KEY_PROGRESS_CURRENT, progress.current)
                    .putInt(KEY_PROGRESS_TOTAL, progress.total)
                    .build()
            )
        }

        return if (lastProgress == IndexingPhase.COMPLETE) {
            Result.success()
        } else {
            Result.failure(
                Data.Builder().putString(KEY_ERROR_MESSAGE, "Indexing did not complete").build()
            )
        }
    }
}
