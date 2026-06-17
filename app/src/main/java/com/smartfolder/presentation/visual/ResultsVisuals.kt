package com.smartfolder.presentation.visual

import com.smartfolder.domain.model.SuggestionItem

data class ResultsOverviewContent(
    val title: String,
    val summary: String,
    val actionLabel: String
)

object ResultsVisuals {

    fun buildOverview(
        filteredSuggestions: List<SuggestionItem>,
        destinationGroupCount: Int,
        selectedCount: Int
    ): ResultsOverviewContent {
        val unassignedCount = filteredSuggestions.count { it.suggestedDestinationId == 0L }
        val summary = when {
            selectedCount > 0 -> "$destinationGroupCount destination groups, $selectedCount selected"
            unassignedCount > 0 -> "$destinationGroupCount destination groups, $unassignedCount images need manual routing"
            else -> "$destinationGroupCount destination groups ready to review"
        }
        val actionLabel = if (selectedCount > 0) {
            "Move $selectedCount selected images"
        } else {
            "Select images to move"
        }
        return ResultsOverviewContent(
            title = "Sort review",
            summary = summary,
            actionLabel = actionLabel
        )
    }
}
