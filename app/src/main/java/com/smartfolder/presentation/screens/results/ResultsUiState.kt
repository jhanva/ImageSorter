package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.model.SuggestionSortMode

enum class ReviewStatusFilter {
    ALL,
    PENDING,
    REVIEWED
}

data class DestinationSuggestionSection(
    val destination: Folder,
    val suggestions: List<SuggestionItem>
)

data class MoveSummary(
    val moved: Int,
    val copiedOnly: Int,
    val failed: Int,
    val restored: Int = 0
)

data class ResultsUiState(
    val allSuggestions: List<SuggestionItem> = emptyList(),
    val filteredSuggestions: List<SuggestionItem> = emptyList(),
    val destinationFolders: List<Folder> = emptyList(),
    val destinationSections: List<DestinationSuggestionSection> = emptyList(),
    val threshold: Float = 0.80f,
    val sortMode: SuggestionSortMode = SuggestionSortMode.BY_SCORE,
    val statusFilter: ReviewStatusFilter = ReviewStatusFilter.ALL,
    val selectedIds: Set<Long> = emptySet(),
    val destinationOverrides: Map<Long, Long> = emptyMap(),
    val collapsedSectionIds: Set<Long> = emptySet(),
    val isMoving: Boolean = false,
    val moveSummary: MoveSummary? = null,
    val canUndo: Boolean = false,
    val error: String? = null
) {
    val selectedCount: Int get() = selectedIds.size
}
