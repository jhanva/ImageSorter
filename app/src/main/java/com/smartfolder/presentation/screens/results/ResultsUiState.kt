package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.SuggestionItem

data class ResultsUiState(
    val allSuggestions: List<SuggestionItem> = emptyList(),
    val filteredSuggestions: List<SuggestionItem> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val threshold: Float = 0.80f,
    val isMoving: Boolean = false,
    val moveResultMessage: String? = null,
    val error: String? = null
)
