package com.smartfolder.presentation.screens.results

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfolder.BuildConfig
import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.FolderRole
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.FolderRepository
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.domain.usecase.AcceptSuggestionUseCase
import com.smartfolder.domain.usecase.BuildManualSuggestionsUseCase
import com.smartfolder.domain.usecase.GetSuggestionsUseCase
import com.smartfolder.domain.usecase.LoadSuggestionsUseCase
import com.smartfolder.domain.usecase.MoveImagesUseCase
import com.smartfolder.domain.usecase.RejectSuggestionUseCase
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
class ResultsViewModel @Inject constructor(
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val moveImagesUseCase: MoveImagesUseCase,
    private val acceptSuggestionUseCase: AcceptSuggestionUseCase,
    private val rejectSuggestionUseCase: RejectSuggestionUseCase,
    private val folderRepository: FolderRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val settingsRepository: SettingsRepository,
    private val buildManualSuggestionsUseCase: BuildManualSuggestionsUseCase,
    private val loadSuggestionsUseCase: LoadSuggestionsUseCase,
    private val suggestionRepository: SuggestionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()
    private var manualDuplicateGroupKeys: Map<Long, String> = emptyMap()
    private var manualVisualGroupKeys: Map<Long, String> = emptyMap()
    private var manualClusterJob: Job? = null
    private var applyFilterJob: Job? = null

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
                refreshManualVisualGroups()
                applyFilter()
            }
        }
        viewModelScope.launch {
            val suggestions = loadInitialSuggestions()
            if (suggestions.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(allSuggestions = suggestions)
                refreshManualVisualGroups()
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

    fun selectBestInVisibleDuplicateGroups() {
        if (!_uiState.value.manualMode) return
        val bestIds = ManualReviewOrganizer.selectBestInDuplicateGroups(
            suggestions = _uiState.value.filteredSuggestions,
            duplicateGroupKeys = manualDuplicateGroupKeys
        )
        if (bestIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(selectedIds = bestIds)
    }

    fun selectBestInVisibleVisualGroups() {
        if (!_uiState.value.manualMode) return
        val bestIds = ManualReviewOrganizer.selectBestInVisualGroups(
            suggestions = _uiState.value.filteredSuggestions,
            visualGroupKeys = manualVisualGroupKeys
        )
        if (bestIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(selectedIds = bestIds)
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

    private fun refreshManualVisualGroups() {
        manualClusterJob?.cancel()
        if (!_uiState.value.manualMode || _uiState.value.allSuggestions.size < 2) {
            manualDuplicateGroupKeys = emptyMap()
            manualVisualGroupKeys = emptyMap()
            _uiState.value = _uiState.value.copy(
                isComputingManualVisualGroups = false
            )
            return
        }

        val suggestionsSnapshot = _uiState.value.allSuggestions
        val snapshotIds = suggestionsSnapshot.map { it.image.id }
        manualClusterJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isComputingManualVisualGroups = true)
            val embeddingsByImageId = loadManualEmbeddings(snapshotIds)
            val clusterResult = withContext(Dispatchers.Default) {
                ManualVisualClusterer.clusterSuggestions(
                    suggestions = suggestionsSnapshot,
                    embeddingsByImageId = embeddingsByImageId
                )
            }

            val currentIds = _uiState.value.allSuggestions.map { it.image.id }
            if (currentIds != snapshotIds || !_uiState.value.manualMode) {
                return@launch
            }

            manualDuplicateGroupKeys = clusterResult.duplicateGroupKeys
            manualVisualGroupKeys = clusterResult.visualGroupKeys
            _uiState.value = _uiState.value.copy(isComputingManualVisualGroups = false)
            applyFilter()
        }
    }

    private suspend fun loadManualEmbeddings(imageIds: List<Long>): Map<Long, Embedding> {
        val modelChoice = settingsRepository.modelChoice.first()
        val embeddings = embeddingRepository.getByImageIds(imageIds)
        if (embeddings.isEmpty()) return emptyMap()

        val preferredByImageId = embeddings
            .filter { it.modelName == modelChoice.modelFileName }
            .groupBy { it.imageId }
            .mapValues { (_, values) -> values.maxByOrNull { it.createdAt }!! }

        if (preferredByImageId.size == imageIds.size) {
            return preferredByImageId
        }

        val latestByImageId = embeddings
            .groupBy { it.imageId }
            .mapValues { (_, values) -> values.maxByOrNull { it.createdAt }!! }

        return imageIds.mapNotNull { imageId ->
            val embedding = preferredByImageId[imageId] ?: latestByImageId[imageId]
            embedding?.let { imageId to it }
        }.toMap()
    }

    private suspend fun loadInitialSuggestions(): List<SuggestionItem> {
        val stored = loadSuggestionsUseCase()
        if (stored.isNotEmpty()) {
            return stored
        }

        if (!settingsRepository.manualMode.first()) {
            return emptyList()
        }

        val unsortedFolder = folderRepository.getByRole(FolderRole.UNSORTED).maxByOrNull { it.id }
            ?: return emptyList()
        return buildManualSuggestionsUseCase(unsortedFolder)
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
        moveImagesToReference(_uiState.value.moveCandidateIds)
    }

    fun moveImagesToReference(imageIds: Set<Long>) {
        viewModelScope.launch {
            val refFolder = folderRepository.getByRole(FolderRole.REFERENCE).maxByOrNull { it.id }
            if (refFolder == null) {
                _uiState.value = _uiState.value.copy(error = "Reference folder not found")
                return@launch
            }
            moveImagesToDestination(
                imageIds = imageIds,
                destinationFolderUri = refFolder.uri,
                destinationLabel = "A"
            )
        }
    }

    fun moveImagesToDestination(
        imageIds: Set<Long>,
        destinationFolderUri: Uri,
        destinationLabel: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMoving = true, error = null)
            val acceptedImages = _uiState.value.allSuggestions
                .filter { it.image.id in imageIds }
                .map { it.image }
            if (acceptedImages.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isMoving = false,
                    error = "No images selected for move"
                )
                return@launch
            }

            val report = moveImagesUseCase(acceptedImages, destinationFolderUri)

            val message = buildString {
                append("Moved to $destinationLabel: ${report.moved}")
                if (report.copiedOnly > 0) append(", Copied only: ${report.copiedOnly}")
                if (report.failed > 0) append(", Failed: ${report.failed}")
            }
            val issueMessage = report.errors
                .take(3)
                .joinToString("\n")
                .takeIf { it.isNotBlank() }

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
                moveResultMessage = message,
                error = issueMessage
            )
            applyFilter()
            persistSuggestions(remaining)
        }
    }

    fun getAcceptedImageUris(): List<Uri> {
        return getImageUris(_uiState.value.moveCandidateIds)
    }

    fun getImageUris(imageIds: Set<Long>): List<Uri> {
        return _uiState.value.allSuggestions
            .filter { it.image.id in imageIds }
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
        applyFilterJob?.cancel()
        val stateSnapshot = _uiState.value
        val duplicateKeysSnapshot = manualDuplicateGroupKeys
        val visualKeysSnapshot = manualVisualGroupKeys

        applyFilterJob = viewModelScope.launch {
            if (stateSnapshot.manualMode) {
                val manualReview = withContext(Dispatchers.Default) {
                    ManualReviewOrganizer.organize(
                        suggestions = stateSnapshot.allSuggestions,
                        query = stateSnapshot.manualQuery,
                        filter = stateSnapshot.manualFilter,
                        sort = stateSnapshot.manualSort,
                        duplicateGroupKeys = duplicateKeysSnapshot,
                        visualGroupKeys = visualKeysSnapshot
                    )
                }
                val latestState = _uiState.value
                val latestIds = latestState.allSuggestions.map { it.image.id }
                val snapshotIds = stateSnapshot.allSuggestions.map { it.image.id }
                if (!latestState.manualMode ||
                    latestIds != snapshotIds ||
                    latestState.manualQuery != stateSnapshot.manualQuery ||
                    latestState.manualFilter != stateSnapshot.manualFilter ||
                    latestState.manualSort != stateSnapshot.manualSort
                ) {
                    return@launch
                }

                val visibleIds = manualReview.visibleSuggestions.mapTo(linkedSetOf()) { it.image.id }
                _uiState.value = latestState.copy(
                    filteredSuggestions = manualReview.visibleSuggestions,
                    manualSections = manualReview.sections,
                    manualGridEntries = manualReview.gridEntries,
                    manualDuplicateGroupCount = manualReview.duplicateGroupCount,
                    manualVisualGroupCount = manualReview.visualGroupCount,
                    manualBatchCount = manualReview.batchCount,
                    manualLargeFileCount = manualReview.largeFileCount,
                    manualVisibleDuplicateGroupCount = manualReview.visibleDuplicateGroupCount,
                    manualVisibleVisualGroupCount = manualReview.visibleVisualGroupCount,
                    manualVisibleBatchCount = manualReview.visibleBatchCount,
                    selectedIds = latestState.selectedIds.filterTo(linkedSetOf()) { it in visibleIds },
                    isDebugTopFallback = false
                )
                return@launch
            }

            val filtered = withContext(Dispatchers.Default) {
                getSuggestionsUseCase(
                    stateSnapshot.allSuggestions,
                    stateSnapshot.threshold
                )
            }
            val latestState = _uiState.value
            if (latestState.manualMode ||
                latestState.threshold != stateSnapshot.threshold ||
                latestState.allSuggestions.map { it.image.id } != stateSnapshot.allSuggestions.map { it.image.id }
            ) {
                return@launch
            }

            if (BuildConfig.DEBUG && filtered.isEmpty() && stateSnapshot.allSuggestions.isNotEmpty()) {
                val top = stateSnapshot.allSuggestions
                    .sortedByDescending { it.score }
                    .take(10)
                _uiState.value = latestState.copy(
                    filteredSuggestions = top,
                    isDebugTopFallback = true
                )
            } else {
                _uiState.value = latestState.copy(
                    filteredSuggestions = filtered,
                    manualSections = emptyList(),
                    manualGridEntries = emptyList(),
                    manualDuplicateGroupCount = 0,
                    manualVisualGroupCount = 0,
                    manualBatchCount = 0,
                    manualLargeFileCount = 0,
                    manualVisibleDuplicateGroupCount = 0,
                    manualVisibleVisualGroupCount = 0,
                    manualVisibleBatchCount = 0,
                    isDebugTopFallback = false
                )
            }
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
