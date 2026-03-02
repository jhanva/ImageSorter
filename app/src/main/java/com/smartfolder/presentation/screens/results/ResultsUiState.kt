package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.SuggestionItem

data class ResultsUiState(
    val allSuggestions: List<SuggestionItem> = emptyList(),
    val filteredSuggestions: List<SuggestionItem> = emptyList(),
    val threshold: Float = 0.80f,
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

    val acceptedCount: Int get() = acceptedIds.size
    val skippedCount: Int get() = skippedIds.size
}
