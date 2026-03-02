package com.smartfolder.presentation.screens.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.usecase.AcceptSuggestionUseCase
import com.smartfolder.domain.usecase.GetSuggestionsUseCase
import com.smartfolder.domain.usecase.MoveImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val moveImagesUseCase: MoveImagesUseCase,
    private val acceptSuggestionUseCase: AcceptSuggestionUseCase,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val threshold = settingsRepository.threshold.first()
            _uiState.value = _uiState.value.copy(threshold = threshold)
        }
    }

    fun setSuggestions(suggestions: List<SuggestionItem>) {
        _uiState.value = _uiState.value.copy(allSuggestions = suggestions)
        applyFilter()
    }

    fun setThreshold(threshold: Float) {
        viewModelScope.launch {
            settingsRepository.setThreshold(threshold)
            _uiState.value = _uiState.value.copy(threshold = threshold)
            applyFilter()
        }
    }

    fun toggleSelection(imageId: Long) {
        val current = _uiState.value.selectedIds.toMutableSet()
        if (current.contains(imageId)) {
            current.remove(imageId)
        } else {
            current.add(imageId)
        }
        _uiState.value = _uiState.value.copy(selectedIds = current)
    }

    fun selectAll() {
        val allIds = _uiState.value.filteredSuggestions.map { it.image.id }.toSet()
        _uiState.value = _uiState.value.copy(selectedIds = allIds)
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedIds = emptySet())
    }

    fun moveSelected() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMoving = true, error = null)

            val refFolder = folderRepository.getByRole(FolderRole.REFERENCE).firstOrNull()
            if (refFolder == null) {
                _uiState.value = _uiState.value.copy(
                    isMoving = false,
                    error = "Reference folder not found"
                )
                return@launch
            }

            val selectedSuggestions = _uiState.value.filteredSuggestions
                .filter { it.image.id in _uiState.value.selectedIds }

            // Record decisions
            for (suggestion in selectedSuggestions) {
                acceptSuggestionUseCase(suggestion)
            }

            val images = selectedSuggestions.map { it.image }
            val report = moveImagesUseCase(images, refFolder.uri)

            val message = buildString {
                append("Moved: ${report.moved}")
                if (report.copiedOnly > 0) append(", Copied only: ${report.copiedOnly}")
                if (report.failed > 0) append(", Failed: ${report.failed}")
            }

            // Remove moved images from suggestions
            val remaining = _uiState.value.allSuggestions
                .filter { it.image.id !in _uiState.value.selectedIds }

            _uiState.value = _uiState.value.copy(
                isMoving = false,
                allSuggestions = remaining,
                selectedIds = emptySet(),
                moveResultMessage = message
            )
            applyFilter()
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(moveResultMessage = null, error = null)
    }

    private fun applyFilter() {
        val filtered = getSuggestionsUseCase(
            _uiState.value.allSuggestions,
            _uiState.value.threshold
        )
        _uiState.value = _uiState.value.copy(filteredSuggestions = filtered)
    }
}
