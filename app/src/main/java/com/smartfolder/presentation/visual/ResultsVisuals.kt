package com.smartfolder.presentation.visual

import com.smartfolder.domain.model.SuggestionItem

data class ResultsOverviewContent(
    val destinationGroupCount: Int,
    val unassignedCount: Int,
    val selectedCount: Int
)

object ResultsVisuals {

    fun buildOverview(
        filteredSuggestions: List<SuggestionItem>,
        destinationGroupCount: Int,
        selectedCount: Int
    ): ResultsOverviewContent {
        return ResultsOverviewContent(
            destinationGroupCount = destinationGroupCount,
            unassignedCount = filteredSuggestions.count { it.suggestedDestinationId == 0L },
            selectedCount = selectedCount
        )
    }
}
