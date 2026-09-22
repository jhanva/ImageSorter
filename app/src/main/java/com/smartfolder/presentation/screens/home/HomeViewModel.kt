package com.smartfolder.presentation.screens.home

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.data.media.MediaStoreFolderProvider
import com.smartfolder.data.storage.AllFilesAccess
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.repository.FolderRepository
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
    private val folderRepository: FolderRepository,
    private val mediaStoreFolderProvider: MediaStoreFolderProvider,
    private val allFilesAccess: AllFilesAccess
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeFolders()
        refreshPermission()
    }

    /**
     * All files access is granted outside the app, so it is re-read whenever
     * the screen comes back to the foreground.
     */
    fun refreshPermission() {
        val granted = allFilesAccess.isGranted()
        _uiState.value = _uiState.value.copy(
            hasAllFilesAccess = granted,
            canStartTriage = granted && _uiState.value.sourceFolders.isNotEmpty()
        )
    }

    fun permissionSettingsIntents(): List<Intent> = allFilesAccess.settingsIntents()

    private fun observeFolders() {
        viewModelScope.launch {
            folderRepository.observeAll().collect { folders ->
                val sourceFolders = folders
                    .filter { it.role == FolderRole.SOURCE }
                    .sortedBy { it.id }

                _uiState.value = _uiState.value.copy(
                    sourceFolders = sourceFolders,
                    canStartTriage = allFilesAccess.isGranted() && sourceFolders.isNotEmpty()
                )
            }
        }
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
}
