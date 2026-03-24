package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.SuggestionItem

data class ResultsUiState(
    val allSuggestions: List<SuggestionItem> = emptyList(),
    val filteredSuggestions: List<SuggestionItem> = emptyList(),
    val isDebugTopFallback: Boolean = false,
    val threshold: Float = 0.80f,
    val manualMode: Boolean = false,
    val manualQuery: String = "",
    val manualFilter: ManualReviewFilter = ManualReviewFilter.ALL,
    val manualSort: ManualReviewSort = ManualReviewSort.BATCHES,
    val manualSections: List<ManualReviewSection> = emptyList(),
    val manualGridEntries: List<ManualReviewGridEntry> = emptyList(),
    val manualNameGroupCount: Int = 0,
    val manualBatchCount: Int = 0,
    val manualLargeFileCount: Int = 0,
    val manualVisibleNameGroupCount: Int = 0,
    val manualVisibleBatchCount: Int = 0,
    val selectedIds: Set<Long> = emptySet(),
    // Review mode: one-by-one image review
    val isReviewing: Boolean = false,
    val currentReviewIndex: Int = 0,
    val acceptedIds: Set<Long> = emptySet(),
    val skippedIds: Set<Long> = emptySet(),
    val reviewComplete: Boolean = false,
    // Move
    val isMoving: Boolean = false,
    val moveResultMessage: String? = null,
    val error: String? = null
) {
    val currentSuggestion: SuggestionItem?
        get() = filteredSuggestions.getOrNull(currentReviewIndex)

    val reviewProgress: String
        get() = if (filteredSuggestions.isNotEmpty()) {
            "${currentReviewIndex + 1} / ${filteredSuggestions.size}"
        } else ""

    val visibleSuggestionCount: Int get() = filteredSuggestions.size
    val selectedCount: Int get() = selectedIds.size
    val acceptedCount: Int get() = acceptedIds.size
    val skippedCount: Int get() = skippedIds.size
    val moveCandidateIds: Set<Long>
        get() = if (manualMode) selectedIds else acceptedIds
    val moveCandidateCount: Int
        get() = moveCandidateIds.size
}
