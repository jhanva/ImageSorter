package com.smartfolder.presentation.screens.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.usecase.AcceptSuggestionUseCase
import com.smartfolder.domain.usecase.GetSuggestionsUseCase
import com.smartfolder.domain.usecase.LoadSuggestionsUseCase
import com.smartfolder.domain.usecase.MoveImagesUseCase
import com.smartfolder.domain.usecase.RejectSuggestionUseCase
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
    private val rejectSuggestionUseCase: RejectSuggestionUseCase,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val loadSuggestionsUseCase: LoadSuggestionsUseCase,
    private val suggestionRepository: SuggestionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val threshold = settingsRepository.threshold.first()
            _uiState.value = _uiState.value.copy(threshold = threshold)
            applyFilter()
        }
        viewModelScope.launch {
            val stored = loadSuggestionsUseCase()
            if (stored.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(allSuggestions = stored)
                applyFilter()
            }
        }
    }

    fun setThreshold(threshold: Float) {
        viewModelScope.launch {
            settingsRepository.setThreshold(threshold)
            _uiState.value = _uiState.value.copy(threshold = threshold)
            applyFilter()
        }
    }

    fun startReview() {
        _uiState.value = _uiState.value.copy(
            isReviewing = true,
            currentReviewIndex = 0,
            acceptedIds = emptySet(),
            skippedIds = emptySet(),
            reviewComplete = false
        )
    }

    fun acceptCurrent() {
        val current = _uiState.value.currentSuggestion ?: return
        viewModelScope.launch {
            acceptSuggestionUseCase(current)
        }
        val newAccepted = _uiState.value.acceptedIds + current.image.id
        _uiState.value = _uiState.value.copy(acceptedIds = newAccepted)
        advanceReview()
    }

    fun skipCurrent() {
        val current = _uiState.value.currentSuggestion ?: return
        viewModelScope.launch {
            rejectSuggestionUseCase(current)
        }
        val newSkipped = _uiState.value.skippedIds + current.image.id
        _uiState.value = _uiState.value.copy(skippedIds = newSkipped)
        advanceReview()
    }

    private fun advanceReview() {
        val nextIndex = _uiState.value.currentReviewIndex + 1
        if (nextIndex >= _uiState.value.filteredSuggestions.size) {
            _uiState.value = _uiState.value.copy(reviewComplete = true)
        } else {
            _uiState.value = _uiState.value.copy(currentReviewIndex = nextIndex)
        }
    }

    fun moveAccepted() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMoving = true, error = null)

            val refFolder = folderRepository.getByRole(FolderRole.REFERENCE).maxByOrNull { it.id }
            if (refFolder == null) {
                _uiState.value = _uiState.value.copy(
                    isMoving = false,
                    error = "Reference folder not found"
                )
                return@launch
            }

            val acceptedImages = _uiState.value.filteredSuggestions
                .filter { it.image.id in _uiState.value.acceptedIds }
                .map { it.image }

            val report = moveImagesUseCase(acceptedImages, refFolder.uri)

            val message = buildString {
                append("Moved: ${report.moved}")
                if (report.copiedOnly > 0) append(", Copied only: ${report.copiedOnly}")
                if (report.failed > 0) append(", Failed: ${report.failed}")
            }

            val remaining = _uiState.value.allSuggestions
                .filter { it.image.id !in report.movedImageIds }

            _uiState.value = _uiState.value.copy(
                isMoving = false,
                allSuggestions = remaining,
                isReviewing = false,
                reviewComplete = false,
                acceptedIds = emptySet(),
                skippedIds = emptySet(),
                moveResultMessage = message
            )
            applyFilter()
            persistSuggestions(remaining)
        }
    }

    fun cancelReview() {
        _uiState.value = _uiState.value.copy(
            isReviewing = false,
            reviewComplete = false,
            currentReviewIndex = 0,
            acceptedIds = emptySet(),
            skippedIds = emptySet()
        )
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

    private suspend fun persistSuggestions(suggestions: List<SuggestionItem>) {
        val createdAt = System.currentTimeMillis()
        val stored = suggestions.map { suggestion ->
            StoredSuggestion(
                imageId = suggestion.image.id,
                score = suggestion.score,
                centroidScore = suggestion.centroidScore,
                topKScore = suggestion.topKScore,
                topSimilarIds = suggestion.topSimilarFromA.map { it.image.id },
                topSimilarScores = suggestion.topSimilarFromA.map { it.score },
                createdAt = createdAt
            )
        }
        suggestionRepository.replaceAll(stored)
    }
}
