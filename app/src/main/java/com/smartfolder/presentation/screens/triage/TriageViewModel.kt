package com.smartfolder.presentation.screens.triage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.usecase.ListSourceImagesUseCase
import com.smartfolder.domain.usecase.MoveImagesUseCase
import com.smartfolder.domain.usecase.UndoMoveUseCase
import com.smartfolder.domain.repository.FolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TriageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val listSourceImagesUseCase: ListSourceImagesUseCase,
    private val moveImagesUseCase: MoveImagesUseCase,
    private val undoMoveUseCase: UndoMoveUseCase
) : ViewModel() {

    private sealed interface Decision {
        data class Moved(
            val entry: MoveImagesUseCase.MovedEntry,
            val destinationId: Long
        ) : Decision

        data object Skipped : Decision
    }

    private val folderId: Long = checkNotNull(savedStateHandle["folderId"])

    private val _uiState = MutableStateFlow(TriageUiState())
    val uiState: StateFlow<TriageUiState> = _uiState.asStateFlow()

    private val decisions = ArrayDeque<Decision>()

    init {
        viewModelScope.launch {
            try {
                val source = folderRepository.getById(folderId)
                    ?: error("Source folder not found")
                val destinations = folderRepository.getByRole(FolderRole.DESTINATION)
                    .sortedBy { it.id }
                val queue = listSourceImagesUseCase(source)
                _uiState.value = TriageUiState(
                    isLoading = false,
                    sourceFolder = source,
                    destinations = destinations,
                    queue = queue
                )
            } catch (e: SecurityException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No permission to read this folder. Remove it and add it again from the home screen."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Could not load the folder."
                )
            }
        }
    }

    fun moveTo(destinationId: Long) {
        val state = _uiState.value
        if (state.isBusy) return
        val image = state.current ?: return
        val destination = state.destinations.firstOrNull { it.id == destinationId } ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            val report = moveImagesUseCase(listOf(image), destination.uri)
            val entry = report.movedEntries.firstOrNull()
            if (entry != null) {
                decisions.addLast(Decision.Moved(entry, destinationId))
                val current = _uiState.value
                _uiState.value = current.copy(
                    isBusy = false,
                    currentIndex = current.currentIndex + 1,
                    movedCount = current.movedCount + 1,
                    movedByDestination = current.movedByDestination +
                        (destinationId to (current.movedByDestination[destinationId] ?: 0) + 1),
                    canUndo = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = report.errors.firstOrNull() ?: "Could not move ${image.displayName}"
                )
            }
        }
    }

    fun skip() {
        val state = _uiState.value
        if (state.isBusy || state.current == null) return
        decisions.addLast(Decision.Skipped)
        _uiState.value = state.copy(
            currentIndex = state.currentIndex + 1,
            skippedCount = state.skippedCount + 1,
            canUndo = true
        )
    }

    fun undoLast() {
        val state = _uiState.value
        if (state.isBusy) return
        val last = decisions.removeLastOrNull() ?: return

        when (last) {
            is Decision.Skipped -> {
                _uiState.value = state.copy(
                    currentIndex = (state.currentIndex - 1).coerceAtLeast(0),
                    skippedCount = (state.skippedCount - 1).coerceAtLeast(0),
                    canUndo = decisions.isNotEmpty()
                )
            }
            is Decision.Moved -> {
                val sourceUri = state.sourceFolder?.uri ?: return
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(isBusy = true, error = null)
                    val report = undoMoveUseCase(
                        listOf(UndoMoveUseCase.UndoEntry(last.entry, sourceUri))
                    )
                    val current = _uiState.value
                    if (report.restored > 0) {
                        val previousCount = current.movedByDestination[last.destinationId] ?: 0
                        val restoredUri = report.restoredUris[last.entry.image.id]
                        val restoredQueue = if (restoredUri != null) {
                            current.queue.map { item ->
                                if (item.id == last.entry.image.id) item.copy(uri = restoredUri) else item
                            }
                        } else {
                            current.queue
                        }
                        _uiState.value = current.copy(
                            isBusy = false,
                            queue = restoredQueue,
                            currentIndex = (current.currentIndex - 1).coerceAtLeast(0),
                            movedCount = (current.movedCount - 1).coerceAtLeast(0),
                            movedByDestination = if (previousCount <= 1) {
                                current.movedByDestination - last.destinationId
                            } else {
                                current.movedByDestination + (last.destinationId to previousCount - 1)
                            },
                            canUndo = decisions.isNotEmpty(),
                            error = report.errors.firstOrNull()
                        )
                    } else {
                        decisions.addLast(last)
                        _uiState.value = current.copy(
                            isBusy = false,
                            error = report.errors.firstOrNull() ?: "Could not undo the move."
                        )
                    }
                }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
