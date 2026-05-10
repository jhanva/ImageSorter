package com.smartfolder.presentation.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.data.saf.SafManager
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.IndexFolderUseCase
import com.smartfolder.domain.usecase.SelectFolderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val selectFolderUseCase: SelectFolderUseCase,
    private val indexFolderUseCase: IndexFolderUseCase,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val mediaStoreFolderProvider: MediaStoreFolderProvider,
    private val safManager: SafManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var canAnalyzeJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.modelChoice.collect { choice ->
                _uiState.value = _uiState.value.copy(modelChoice = choice)
                updateCanAnalyze()
            }
        }
        viewModelScope.launch {
            settingsRepository.executionProfile.collect { profile ->
                _uiState.value = _uiState.value.copy(executionProfile = profile)
            }
        }
        viewModelScope.launch {
            settingsRepository.manualMode.collect { manualMode ->
                _uiState.value = _uiState.value.copy(manualMode = manualMode)
                updateCanAnalyze()
            }
        }
        observeFolders()
        refreshAvailableImageFolders()
    }

    private fun observeFolders() {
        viewModelScope.launch {
            folderRepository.observeAll().collect { folders ->
                val refFolder = folders
                    .filter { it.role == FolderRole.REFERENCE }
                    .maxByOrNull { it.id }
                val unsortedFolder = folders
                    .filter { it.role == FolderRole.UNSORTED }
                    .maxByOrNull { it.id }

                _uiState.value = _uiState.value.copy(
                    referenceFolder = refFolder,
                    unsortedFolder = unsortedFolder
                )
                updateCanAnalyze()
            }
        }
    }

    fun selectReferenceFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val folder = selectFolderUseCase(uri, FolderRole.REFERENCE)
                _uiState.value = _uiState.value.copy(
                    referenceFolder = folder,
                    error = null
                )
                updateCanAnalyze()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun selectUnsortedFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val folder = selectFolderUseCase(uri, FolderRole.UNSORTED)
                _uiState.value = _uiState.value.copy(
                    unsortedFolder = folder,
                    error = null
                )
                updateCanAnalyze()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun setModelChoice(choice: ModelChoice) {
        viewModelScope.launch {
            settingsRepository.setModelChoice(choice)
        }
    }

    fun indexReferenceFolder() {
        val folder = _uiState.value.referenceFolder ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isIndexingRef = true)
            indexFolderUseCase(
                folder,
                _uiState.value.modelChoice,
                _uiState.value.executionProfile
            ).collect { progress ->
                _uiState.value = _uiState.value.copy(refIndexingProgress = progress)
                if (progress.phase == IndexingPhase.COMPLETE || progress.phase == IndexingPhase.ERROR) {
                    val updated = folderRepository.getById(folder.id)
                    _uiState.value = _uiState.value.copy(
                        isIndexingRef = false,
                        referenceFolder = updated ?: folder,
                        error = if (progress.phase == IndexingPhase.ERROR) progress.errorMessage else null
                    )
                    updateCanAnalyze()
                }
            }
        }
    }

    fun indexUnsortedFolder() {
        val folder = _uiState.value.unsortedFolder ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isIndexingUnsorted = true)
            indexFolderUseCase(
                folder,
                _uiState.value.modelChoice,
                _uiState.value.executionProfile
            ).collect { progress ->
                _uiState.value = _uiState.value.copy(unsortedIndexingProgress = progress)
                if (progress.phase == IndexingPhase.COMPLETE || progress.phase == IndexingPhase.ERROR) {
                    val updated = folderRepository.getById(folder.id)
                    _uiState.value = _uiState.value.copy(
                        isIndexingUnsorted = false,
                        unsortedFolder = updated ?: folder,
                        error = if (progress.phase == IndexingPhase.ERROR) progress.errorMessage else null
                    )
                    updateCanAnalyze()
                }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refreshAvailableImageFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingImageFolders = true)
            val folders = try {
                mediaStoreFolderProvider.getImageFolders()
            } catch (_: SecurityException) {
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(
                availableImageFolders = folders,
                isLoadingImageFolders = false
            )
        }
    }

    private fun updateCanAnalyze() {
        canAnalyzeJob?.cancel()
        val unsorted = _uiState.value.unsortedFolder
        if (unsorted == null) {
            _uiState.value = _uiState.value.copy(canAnalyze = false)
            return
        }
        canAnalyzeJob = viewModelScope.launch {
            if (_uiState.value.manualMode) {
                _uiState.value = _uiState.value.copy(canAnalyze = true)
                return@launch
            }
            val ref = _uiState.value.referenceFolder
            if (ref == null) {
                _uiState.value = _uiState.value.copy(canAnalyze = false)
                return@launch
            }
            val model = _uiState.value.modelChoice
            val refCount = embeddingRepository.countByFolderAndModel(ref.id, model.modelFileName)
            val unsortedCount = embeddingRepository.countByFolderAndModel(unsorted.id, model.modelFileName)
            _uiState.value = _uiState.value.copy(
                canAnalyze = refCount > 0 && unsortedCount > 0
            )
        }
    }
}
