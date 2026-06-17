package com.smartfolder.presentation.screens.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.AnalyzeImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val analyzeImagesUseCase: AnalyzeImagesUseCase,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    fun startAnalysis() {
        if (_uiState.value.isAnalyzing) return
        analysisJob = viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)

                val destinationFolders = folderRepository.getByRole(FolderRole.DESTINATION).sortedBy { it.id }
                val sourceFolders = folderRepository.getByRole(FolderRole.SOURCE).sortedBy { it.id }

                if (destinationFolders.isEmpty() || sourceFolders.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        error = "Select and index at least one destination folder and one source folder."
                    )
                    return@launch
                }

                val modelChoice = settingsRepository.modelChoice.first()
                val executionProfile = settingsRepository.executionProfile.first()
                val threshold = settingsRepository.threshold.first()

                analyzeImagesUseCase(
                    destinationFolders = destinationFolders,
                    sourceFolders = sourceFolders,
                    modelChoice = modelChoice,
                    threshold = threshold,
                    executionProfile = executionProfile
                ).collect { result ->
                    _uiState.value = _uiState.value.copy(
                        progress = result.progress,
                        suggestions = result.suggestions
                    )
                    if (result.progress.phase == AnalysisPhase.COMPLETE) {
                        _uiState.value = _uiState.value.copy(isAnalyzing = false)
                    }
                    if (result.progress.phase == AnalysisPhase.ERROR) {
                        _uiState.value = _uiState.value.copy(
                            isAnalyzing = false,
                            error = result.progress.errorMessage
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = e.message ?: "Unknown error while analyzing the library"
                )
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.value = _uiState.value.copy(isAnalyzing = false)
    }
}
