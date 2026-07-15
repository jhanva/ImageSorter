package com.smartfolder.presentation.screens.trash

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.usecase.DeleteTrashImageUseCase
import com.smartfolder.domain.usecase.ListTrashImagesUseCase
import com.smartfolder.domain.usecase.RestoreFromTrashUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val listTrashImagesUseCase: ListTrashImagesUseCase,
    private val restoreFromTrashUseCase: RestoreFromTrashUseCase,
    private val deleteTrashImageUseCase: DeleteTrashImageUseCase
) : ViewModel() {

    private val folderId: Long = checkNotNull(savedStateHandle["folderId"])

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val source = folderRepository.getById(folderId)
                    ?: error("Source folder not found")
                val items = listTrashImagesUseCase(source)
                _uiState.value = TrashUiState(
                    isLoading = false,
                    sourceFolder = source,
                    items = items
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Could not load the trash folder."
                )
            }
        }
    }

    fun restore(item: ImageInfo) {
        val state = _uiState.value
        if (state.isBusy) return
        val source = state.sourceFolder ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            restoreFromTrashUseCase(item, source).fold(
                onSuccess = {
                    val current = _uiState.value
                    _uiState.value = current.copy(
                        isBusy = false,
                        items = current.items.filterNot { it.id == item.id },
                        restoredCount = current.restoredCount + 1
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = e.message ?: "Could not restore ${item.displayName}"
                    )
                }
            )
        }
    }

    fun deletePermanently(item: ImageInfo) {
        val state = _uiState.value
        if (state.isBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            deleteTrashImageUseCase(item).fold(
                onSuccess = {
                    val current = _uiState.value
                    _uiState.value = current.copy(
                        isBusy = false,
                        items = current.items.filterNot { it.id == item.id }
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = e.message ?: "Could not delete ${item.displayName}"
                    )
                }
            )
        }
    }

    fun emptyTrash() {
        val state = _uiState.value
        if (state.isBusy || state.items.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            val errors = mutableListOf<String>()
            val remaining = mutableListOf<ImageInfo>()
            for (item in _uiState.value.items) {
                deleteTrashImageUseCase(item).fold(
                    onSuccess = { },
                    onFailure = { e ->
                        remaining.add(item)
                        e.message?.let { errors.add(it) }
                    }
                )
            }
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                items = remaining,
                error = errors.firstOrNull()
            )
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
