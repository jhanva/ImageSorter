package com.smartfolder.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.IndexFolderUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

@HiltWorker
class IndexingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val indexFolderUseCase: IndexFolderUseCase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_FOLDER_ID = "folder_id"
        const val KEY_MODEL_CHOICE = "model_choice"
        const val KEY_PROGRESS_PHASE = "progress_phase"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val TIMEOUT_MS = 3_600_000L // 1 hour

        fun buildWorkRequest(folderId: Long, modelChoice: ModelChoice) =
            OneTimeWorkRequestBuilder<IndexingWorker>()
                .setInputData(
                    Data.Builder()
                        .putLong(KEY_FOLDER_ID, folderId)
                        .putString(KEY_MODEL_CHOICE, modelChoice.name)
                        .build()
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()
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
        val executionProfile = settingsRepository.executionProfile.first()

        var lastProgress = IndexingPhase.IDLE

        val completed = withTimeoutOrNull(TIMEOUT_MS) {
            indexFolderUseCase(folder, modelChoice, executionProfile).collect { progress ->
                lastProgress = progress.phase
                setProgress(
                    Data.Builder()
                        .putString(KEY_PROGRESS_PHASE, progress.phase.name)
                        .putInt(KEY_PROGRESS_CURRENT, progress.current)
                        .putInt(KEY_PROGRESS_TOTAL, progress.total)
                        .build()
                )
            }
            true
        }

        if (completed == null) {
            return Result.retry()
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
