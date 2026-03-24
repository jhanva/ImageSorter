package com.smartfolder.presentation.screens.results

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.BuildConfig
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
            settingsRepository.manualMode.collect { manualMode ->
                _uiState.value = _uiState.value.copy(
                    manualMode = manualMode,
                    isReviewing = if (manualMode) false else _uiState.value.isReviewing,
                    reviewComplete = if (manualMode) false else _uiState.value.reviewComplete,
                    currentReviewIndex = if (manualMode) 0 else _uiState.value.currentReviewIndex,
                    selectedIds = if (manualMode) _uiState.value.selectedIds else emptySet()
                )
                applyFilter()
            }
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
        if (_uiState.value.manualMode) return
        _uiState.value = _uiState.value.copy(
            isReviewing = true,
            currentReviewIndex = 0,
            acceptedIds = emptySet(),
            skippedIds = emptySet(),
            reviewComplete = false
        )
    }

    fun acceptCurrent() {
        if (_uiState.value.manualMode) return
        val current = _uiState.value.currentSuggestion ?: return
        viewModelScope.launch {
            acceptSuggestionUseCase(current)
        }
        val newAccepted = _uiState.value.acceptedIds + current.image.id
        _uiState.value = _uiState.value.copy(acceptedIds = newAccepted)
        advanceReview()
    }

    fun skipCurrent() {
        if (_uiState.value.manualMode) return
        val current = _uiState.value.currentSuggestion ?: return
        viewModelScope.launch {
            rejectSuggestionUseCase(current)
        }
        val newSkipped = _uiState.value.skippedIds + current.image.id
        _uiState.value = _uiState.value.copy(skippedIds = newSkipped)
        advanceReview()
    }

    fun finishReviewNow() {
        if (_uiState.value.manualMode) return
        _uiState.value = _uiState.value.copy(
            isReviewing = false,
            reviewComplete = true
        )
    }

    fun toggleSelection(imageId: Long) {
        if (!_uiState.value.manualMode) return
        val selectedIds = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (imageId in selectedIds) {
                selectedIds - imageId
            } else {
                selectedIds + imageId
            }
        )
    }

    fun toggleSectionSelection(imageIds: Set<Long>) {
        if (!_uiState.value.manualMode || imageIds.isEmpty()) return
        val selectedIds = _uiState.value.selectedIds
        val nextSelectedIds = if (imageIds.all { it in selectedIds }) {
            selectedIds - imageIds
        } else {
            selectedIds + imageIds
        }
        _uiState.value = _uiState.value.copy(selectedIds = nextSelectedIds)
    }

    fun selectAllFiltered() {
        if (!_uiState.value.manualMode) return
        _uiState.value = _uiState.value.copy(
            selectedIds = _uiState.value.filteredSuggestions.mapTo(linkedSetOf()) { it.image.id }
        )
    }

    fun clearSelection() {
        if (!_uiState.value.manualMode) return
        _uiState.value = _uiState.value.copy(selectedIds = emptySet())
    }

    fun setManualQuery(query: String) {
        if (!_uiState.value.manualMode) return
        _uiState.value = _uiState.value.copy(manualQuery = query)
        applyFilter()
    }

    fun setManualFilter(filter: ManualReviewFilter) {
        if (!_uiState.value.manualMode) return
        _uiState.value = _uiState.value.copy(manualFilter = filter)
        applyFilter()
    }

    fun setManualSort(sort: ManualReviewSort) {
        if (!_uiState.value.manualMode) return
        _uiState.value = _uiState.value.copy(manualSort = sort)
        applyFilter()
    }

    private fun advanceReview() {
        val nextIndex = _uiState.value.currentReviewIndex + 1
        if (nextIndex >= _uiState.value.filteredSuggestions.size) {
            _uiState.value = _uiState.value.copy(
                isReviewing = false,
                reviewComplete = true
            )
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

            val selectedIds = _uiState.value.moveCandidateIds
            val acceptedImages = _uiState.value.filteredSuggestions
                .filter { it.image.id in selectedIds }
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
                selectedIds = emptySet(),
                acceptedIds = emptySet(),
                skippedIds = emptySet(),
                moveResultMessage = message
            )
            applyFilter()
            persistSuggestions(remaining)
        }
    }

    fun getAcceptedImageUris(): List<Uri> {
        val selectedIds = _uiState.value.moveCandidateIds
        return _uiState.value.filteredSuggestions
            .filter { it.image.id in selectedIds }
            .map { it.image.uri }
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    fun cancelReview() {
        if (_uiState.value.manualMode) {
            _uiState.value = _uiState.value.copy(selectedIds = emptySet())
            return
        }
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
        if (_uiState.value.manualMode) {
            val manualReview = ManualReviewOrganizer.organize(
                suggestions = _uiState.value.allSuggestions,
                query = _uiState.value.manualQuery,
                filter = _uiState.value.manualFilter,
                sort = _uiState.value.manualSort
            )
            val visibleIds = manualReview.visibleSuggestions.mapTo(linkedSetOf()) { it.image.id }
            _uiState.value = _uiState.value.copy(
                filteredSuggestions = manualReview.visibleSuggestions,
                manualSections = manualReview.sections,
                manualGridEntries = manualReview.gridEntries,
                manualNameGroupCount = manualReview.nameGroupCount,
                manualBatchCount = manualReview.batchCount,
                manualLargeFileCount = manualReview.largeFileCount,
                selectedIds = _uiState.value.selectedIds.filterTo(linkedSetOf()) { it in visibleIds },
                isDebugTopFallback = false
            )
            return
        }
        val filtered = getSuggestionsUseCase(
            _uiState.value.allSuggestions,
            _uiState.value.threshold
        )
        if (BuildConfig.DEBUG && filtered.isEmpty() && _uiState.value.allSuggestions.isNotEmpty()) {
            val top = _uiState.value.allSuggestions
                .sortedByDescending { it.score }
                .take(10)
            _uiState.value = _uiState.value.copy(
                filteredSuggestions = top,
                isDebugTopFallback = true
            )
        } else {
            _uiState.value = _uiState.value.copy(
                filteredSuggestions = filtered,
                manualSections = emptyList(),
                manualGridEntries = emptyList(),
                manualNameGroupCount = 0,
                manualBatchCount = 0,
                manualLargeFileCount = 0,
                isDebugTopFallback = false
            )
        }
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
