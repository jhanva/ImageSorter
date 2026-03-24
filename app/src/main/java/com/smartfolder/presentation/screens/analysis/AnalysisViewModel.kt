package com.smartfolder.presentation.screens.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.AnalyzeImagesUseCase
import com.smartfolder.domain.usecase.BuildManualSuggestionsUseCase
import com.smartfolder.domain.usecase.IndexFolderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val analyzeImagesUseCase: AnalyzeImagesUseCase,
    private val buildManualSuggestionsUseCase: BuildManualSuggestionsUseCase,
    private val indexFolderUseCase: IndexFolderUseCase,
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

                val unsortedFolder = folderRepository.getByRole(FolderRole.UNSORTED).maxByOrNull { it.id }
                val manualMode = settingsRepository.manualMode.first()

                if (manualMode && unsortedFolder == null) {
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        error = "Unsorted folder must be selected"
                    )
                    return@launch
                }

                val refFolder = folderRepository.getByRole(FolderRole.REFERENCE).maxByOrNull { it.id }
                if (!manualMode && (refFolder == null || unsortedFolder == null)) {
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        error = "Both folders must be selected"
                    )
                    return@launch
                }
                val resolvedUnsortedFolder = unsortedFolder ?: return@launch

                if (manualMode) {
                    val modelChoice = settingsRepository.modelChoice.first()
                    val executionProfile = settingsRepository.executionProfile.first()
                    var indexingFailed = false

                    indexFolderUseCase(
                        folder = resolvedUnsortedFolder,
                        modelChoice = modelChoice,
                        executionProfile = executionProfile
                    ).collect { progress ->
                        _uiState.value = _uiState.value.copy(
                            progress = _uiState.value.progress.copy(
                                phase = if (progress.phase == com.smartfolder.domain.model.IndexingPhase.ERROR) {
                                    AnalysisPhase.ERROR
                                } else {
                                    AnalysisPhase.INDEXING_UNSORTED
                                },
                                current = progress.current,
                                total = progress.total,
                                currentFileName = progress.currentFileName,
                                errorMessage = progress.errorMessage
                            )
                        )
                        if (progress.phase == com.smartfolder.domain.model.IndexingPhase.ERROR) {
                            indexingFailed = true
                            _uiState.value = _uiState.value.copy(
                                isAnalyzing = false,
                                error = progress.errorMessage ?: "Failed to prepare assisted review"
                            )
                            return@collect
                        }
                    }
                    if (indexingFailed) return@launch

                    val suggestions = withContext(Dispatchers.IO) {
                        buildManualSuggestionsUseCase(resolvedUnsortedFolder)
                    }
                    _uiState.value = _uiState.value.copy(
                        suggestions = suggestions,
                        progress = _uiState.value.progress.copy(
                            phase = AnalysisPhase.COMPLETE,
                            current = suggestions.size,
                            total = suggestions.size
                        ),
                        isAnalyzing = false
                    )
                    return@launch
                }

                val modelChoice = settingsRepository.modelChoice.first()
                val executionProfile = settingsRepository.executionProfile.first()
                val threshold = settingsRepository.threshold.first()
                val resolvedRefFolder = refFolder ?: return@launch

                analyzeImagesUseCase(
                    referenceFolder = resolvedRefFolder,
                    unsortedFolder = resolvedUnsortedFolder,
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
                    error = e.message ?: "Unknown error while loading manual suggestions"
                )
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.value = _uiState.value.copy(isAnalyzing = false)
    }
}
