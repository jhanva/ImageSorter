package com.smartfolder.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.AnalyzeImagesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

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
        var lastPhase = AnalysisPhase.IDLE

        analyzeImagesUseCase(
            referenceFolder = refFolder,
            unsortedFolder = unsortedFolder,
            modelChoice = modelChoice,
            threshold = threshold
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

        return if (lastPhase == AnalysisPhase.COMPLETE) {
            Result.success()
        } else {
            Result.failure(
                Data.Builder().putString(KEY_ERROR_MESSAGE, "Analysis did not complete").build()
            )
        }
    }
}
