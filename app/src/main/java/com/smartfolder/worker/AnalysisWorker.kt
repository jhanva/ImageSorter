package com.smartfolder.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.AnalyzeImagesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val analyzeImagesUseCase: AnalyzeImagesUseCase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_REF_FOLDER_ID = "ref_folder_id"
        const val KEY_UNSORTED_FOLDER_ID = "unsorted_folder_id"
        const val KEY_MODEL_CHOICE = "model_choice"
        const val KEY_PROGRESS_PHASE = "progress_phase"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val TIMEOUT_MS = 1_800_000L // 30 minutes

        fun buildWorkRequest(
            refFolderId: Long,
            unsortedFolderId: Long,
            modelChoice: ModelChoice
        ) = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(KEY_REF_FOLDER_ID, refFolderId)
                    .putLong(KEY_UNSORTED_FOLDER_ID, unsortedFolderId)
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
        val refFolderId = inputData.getLong(KEY_REF_FOLDER_ID, -1)
        val unsortedFolderId = inputData.getLong(KEY_UNSORTED_FOLDER_ID, -1)

        if (refFolderId == -1L || unsortedFolderId == -1L) {
            return Result.failure(
                Data.Builder().putString(KEY_ERROR_MESSAGE, "Invalid folder IDs").build()
            )
        }

        val modelName = inputData.getString(KEY_MODEL_CHOICE) ?: ModelChoice.FAST.name
        val modelChoice = try {
            ModelChoice.valueOf(modelName)
        } catch (e: IllegalArgumentException) {
            ModelChoice.FAST
        }

        val refFolder = folderRepository.getById(refFolderId)
        val unsortedFolder = folderRepository.getById(unsortedFolderId)

        if (refFolder == null || unsortedFolder == null) {
            return Result.failure(
                Data.Builder().putString(KEY_ERROR_MESSAGE, "Folder not found").build()
            )
        }

        val threshold = settingsRepository.threshold.first()
        val executionProfile = settingsRepository.executionProfile.first()
        var lastPhase = AnalysisPhase.IDLE

        val completed = withTimeoutOrNull(TIMEOUT_MS) {
            analyzeImagesUseCase(
                referenceFolder = refFolder,
                unsortedFolder = unsortedFolder,
                modelChoice = modelChoice,
                threshold = threshold,
                executionProfile = executionProfile
            ).collect { result ->
                lastPhase = result.progress.phase
                setProgress(
                    Data.Builder()
                        .putString(KEY_PROGRESS_PHASE, result.progress.phase.name)
                        .putInt(KEY_PROGRESS_CURRENT, result.progress.current)
                        .putInt(KEY_PROGRESS_TOTAL, result.progress.total)
                        .build()
                )
            }
            true
        }

        if (completed == null) {
            return Result.retry()
        }

        return if (lastPhase == AnalysisPhase.COMPLETE) {
            Result.success()
        } else {
            Result.failure(
                Data.Builder().putString(KEY_ERROR_MESSAGE, "Analysis did not complete").build()
            )
        }
    }
}
