package com.smartfolder.presentation.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.IndexFolderUseCase
import com.smartfolder.domain.usecase.SelectFolderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.modelChoice.collect { choice ->
                _uiState.value = _uiState.value.copy(modelChoice = choice)
            }
        }
        loadExistingFolders()
    }

    private fun loadExistingFolders() {
        viewModelScope.launch {
            val refFolders = folderRepository.getByRole(FolderRole.REFERENCE)
            val unsortedFolders = folderRepository.getByRole(FolderRole.UNSORTED)
            _uiState.value = _uiState.value.copy(
                referenceFolder = refFolders.firstOrNull(),
                unsortedFolder = unsortedFolders.firstOrNull(),
                canAnalyze = refFolders.firstOrNull()?.indexedCount?.let { it > 0 } == true
                        && unsortedFolders.firstOrNull()?.indexedCount?.let { it > 0 } == true
            )
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
            indexFolderUseCase(folder, _uiState.value.modelChoice).collect { progress ->
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
            indexFolderUseCase(folder, _uiState.value.modelChoice).collect { progress ->
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

    private fun updateCanAnalyze() {
        val ref = _uiState.value.referenceFolder
        val unsorted = _uiState.value.unsortedFolder
        _uiState.value = _uiState.value.copy(
            canAnalyze = ref != null && unsorted != null
                    && (ref.indexedCount) > 0
                    && (unsorted.indexedCount) > 0
        )
    }
}
