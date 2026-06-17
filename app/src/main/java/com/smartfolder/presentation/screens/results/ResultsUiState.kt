package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.SuggestionItem

data class DestinationSuggestionSection(
    val destination: Folder,
    val suggestions: List<SuggestionItem>
)

data class ResultsUiState(
    val allSuggestions: List<SuggestionItem> = emptyList(),
    val filteredSuggestions: List<SuggestionItem> = emptyList(),
    val destinationFolders: List<Folder> = emptyList(),
    val destinationSections: List<DestinationSuggestionSection> = emptyList(),
    val threshold: Float = 0.80f,
    val selectedIds: Set<Long> = emptySet(),
    val destinationOverrides: Map<Long, Long> = emptyMap(),
    val isMoving: Boolean = false,
    val moveResultMessage: String? = null,
    val error: String? = null
) {
    val selectedCount: Int get() = selectedIds.size
}
