package com.smartfolder.presentation.visual

import android.net.TestUri
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.SuggestionItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsVisualsTest {

    @Test
    fun buildOverview_reportsManualRoutingCount_whenUnassignedSuggestionsExist() {
        val suggestions = listOf(
            suggestion(id = 1, destinationId = 10),
            suggestion(id = 2, destinationId = 0),
            suggestion(id = 3, destinationId = 0)
        )

        val overview = ResultsVisuals.buildOverview(
            filteredSuggestions = suggestions,
            destinationGroupCount = 2,
            selectedCount = 0
        )

        assertEquals("Sort review", overview.title)
        assertEquals("2 destination groups, 2 images need manual routing", overview.summary)
        assertEquals("Select images to move", overview.actionLabel)
    }

    @Test
    fun buildOverview_reportsSelectionCount_whenImagesAreSelected() {
        val suggestions = listOf(
            suggestion(id = 1, destinationId = 10),
            suggestion(id = 2, destinationId = 20)
        )

        val overview = ResultsVisuals.buildOverview(
            filteredSuggestions = suggestions,
            destinationGroupCount = 2,
            selectedCount = 2
        )

        assertEquals("2 destination groups, 2 selected", overview.summary)
        assertEquals("Move 2 selected images", overview.actionLabel)
    }

    private fun suggestion(id: Long, destinationId: Long): SuggestionItem {
        return SuggestionItem(
            image = ImageInfo(
                id = id,
                folderId = 1,
                uri = TestUri("content://image/$id"),
                displayName = "Image $id",
                contentHash = "hash-$id",
                sizeBytes = 1024L,
                lastModified = 1_700_000_000_000L
            ),
            suggestedDestinationId = destinationId,
            score = 0.92f,
            secondBestScore = 0.51f,
            centroidScore = 0.89f,
            topKScore = 0.9f,
            topSimilarImages = emptyList()
        )
    }
}
