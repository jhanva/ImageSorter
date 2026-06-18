package com.smartfolder.presentation.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.domain.model.IndexingProgress
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.IndexFolderUseCase
import com.smartfolder.domain.usecase.SelectFolderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val selectFolderUseCase: SelectFolderUseCase,
    private val indexFolderUseCase: IndexFolderUseCase,
    private val embeddingRepository: EmbeddingRepository,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaStoreFolderProvider: MediaStoreFolderProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.modelChoice.collect { choice ->
                _uiState.value = _uiState.value.copy(modelChoice = choice)
                refreshAnalyzeAvailability()
            }
        }
        viewModelScope.launch {
            settingsRepository.executionProfile.collect { profile ->
                _uiState.value = _uiState.value.copy(executionProfile = profile)
            }
        }
        observeFolders()
        refreshAvailableImageFolders()
    }

    private fun observeFolders() {
        viewModelScope.launch {
            folderRepository.observeAll().collect { folders ->
                val destinationFolders = folders
                    .filter { it.role == FolderRole.DESTINATION }
                    .sortedBy { it.id }
                val sourceFolders = folders
                    .filter { it.role == FolderRole.SOURCE }
                    .sortedBy { it.id }

                _uiState.value = _uiState.value.copy(
                    destinationFolders = destinationFolders,
                    sourceFolders = sourceFolders,
                    canAnalyze = false
                )
                refreshAnalyzeAvailability()
            }
        }
    }

    fun addDestinationFolder(uri: Uri) {
        addFolder(uri, FolderRole.DESTINATION)
    }

    fun addSourceFolder(uri: Uri) {
        addFolder(uri, FolderRole.SOURCE)
    }

    private fun addFolder(uri: Uri, role: FolderRole) {
        viewModelScope.launch {
            try {
                selectFolderUseCase(uri, role)
                _uiState.value = _uiState.value.copy(error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun removeFolder(folder: Folder) {
        viewModelScope.launch {
            folderRepository.delete(folder)
        }
    }

    fun setModelChoice(choice: ModelChoice) {
        viewModelScope.launch {
            settingsRepository.setModelChoice(choice)
        }
    }

    fun indexDestinationFolders() {
        indexFolders(FolderRole.DESTINATION)
    }

    fun indexSourceFolders() {
        indexFolders(FolderRole.SOURCE)
    }

    private fun indexFolders(role: FolderRole) {
        val folders = if (role == FolderRole.DESTINATION) {
            _uiState.value.destinationFolders
        } else {
            _uiState.value.sourceFolders
        }
        if (folders.isEmpty()) return

        viewModelScope.launch {
            updateIndexingState(role, isIndexing = true, progress = IndexingProgress())
            folders.forEachIndexed { index, folder ->
                indexFolderUseCase(
                    folder,
                    _uiState.value.modelChoice,
                    _uiState.value.executionProfile
                ).collect { progress ->
                    updateIndexingState(
                        role = role,
                        isIndexing = progress.phase != IndexingPhase.COMPLETE && progress.phase != IndexingPhase.ERROR,
                        progress = IndexingProgress(
                            phase = progress.phase,
                            current = index + if (progress.phase == IndexingPhase.COMPLETE) 1 else 0,
                            total = folders.size,
                            currentFileName = "[${folder.displayName}] ${progress.currentFileName}".trim(),
                            errorMessage = progress.errorMessage
                        )
                    )
                }
            }
            val latestFolders = folderRepository.getByRole(role)
            _uiState.value = _uiState.value.copy(
                destinationFolders = if (role == FolderRole.DESTINATION) latestFolders.sortedBy { it.id } else _uiState.value.destinationFolders,
                sourceFolders = if (role == FolderRole.SOURCE) latestFolders.sortedBy { it.id } else _uiState.value.sourceFolders,
                canAnalyze = false
            )
            refreshAnalyzeAvailability()
            updateIndexingState(
                role = role,
                isIndexing = false,
                progress = IndexingProgress(
                    phase = IndexingPhase.COMPLETE,
                    current = folders.size,
                    total = folders.size
                )
            )
        }
    }

    private fun updateIndexingState(
        role: FolderRole,
        isIndexing: Boolean,
        progress: IndexingProgress
    ) {
        _uiState.value = if (role == FolderRole.DESTINATION) {
            _uiState.value.copy(
                isIndexingDestinations = isIndexing,
                destinationIndexingProgress = progress,
                error = if (progress.phase == IndexingPhase.ERROR) progress.errorMessage else _uiState.value.error
            )
        } else {
            _uiState.value.copy(
                isIndexingSources = isIndexing,
                sourceIndexingProgress = progress,
                error = if (progress.phase == IndexingPhase.ERROR) progress.errorMessage else _uiState.value.error
            )
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refreshAvailableImageFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingImageFolders = true)
            val folders = withContext(Dispatchers.IO) {
                try {
                    mediaStoreFolderProvider.getImageFolders()
                } catch (_: SecurityException) {
                    emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            _uiState.value = _uiState.value.copy(
                availableImageFolders = folders,
                isLoadingImageFolders = false
            )
        }
    }

    private fun refreshAnalyzeAvailability() {
        viewModelScope.launch {
            val state = _uiState.value
            val canAnalyze = canAnalyze(
                destinationFolders = state.destinationFolders,
                sourceFolders = state.sourceFolders,
                modelChoice = state.modelChoice
            )
            _uiState.value = _uiState.value.copy(canAnalyze = canAnalyze)
        }
    }

    private suspend fun canAnalyze(
        destinationFolders: List<Folder>,
        sourceFolders: List<Folder>,
        modelChoice: ModelChoice
    ): Boolean {
        if (destinationFolders.isEmpty() || sourceFolders.isEmpty()) return false
        return (destinationFolders + sourceFolders).all { folder ->
            if (folder.imageCount <= 0) {
                false
            } else {
                embeddingRepository.countByFolderAndModel(
                    folderId = folder.id,
                    modelName = modelChoice.modelFileName
                ) >= folder.imageCount
            }
        }
    }
}
