package com.smartfolder.presentation.visual

import android.net.TestUri
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.SuggestionItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsVisualsTest {

    @Test
    fun `overview reports unassigned count when suggestions need manual routing`() {
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

        assertEquals(2, overview.destinationGroupCount)
        assertEquals(2, overview.unassignedCount)
        assertEquals(0, overview.selectedCount)
    }

    @Test
    fun `overview reports selection count when images are selected`() {
        val suggestions = listOf(
            suggestion(id = 1, destinationId = 10),
            suggestion(id = 2, destinationId = 20)
        )

        val overview = ResultsVisuals.buildOverview(
            filteredSuggestions = suggestions,
            destinationGroupCount = 2,
            selectedCount = 2
        )

        assertEquals(2, overview.selectedCount)
        assertEquals(0, overview.unassignedCount)
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
