package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.ReviewStatus
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.model.SuggestionSortMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class GetSuggestionsUseCaseTest {

    private val useCase = GetSuggestionsUseCase()

    private fun suggestion(
        id: Long,
        score: Float,
        secondBestScore: Float,
        destinationId: Long = 1L,
        reviewStatus: ReviewStatus = ReviewStatus.PENDING
    ) = SuggestionItem(
        image = ImageInfo(id, 1L, mock(Uri::class.java), "img$id.jpg", "h$id", 10L, 1L),
        suggestedDestinationId = destinationId,
        score = score,
        secondBestScore = secondBestScore,
        centroidScore = score,
        topKScore = score,
        topSimilarImages = emptyList(),
        reviewStatus = reviewStatus
    )

    @Test
    fun `by score keeps assigned first ordered by score descending`() {
        val input = listOf(
            suggestion(1L, score = 0.7f, secondBestScore = 0.1f),
            suggestion(2L, score = 0.9f, secondBestScore = 0.1f),
            suggestion(3L, score = 0.5f, secondBestScore = 0.1f, destinationId = 0L)
        )

        val result = useCase(input, threshold = 0.6f, sortMode = SuggestionSortMode.BY_SCORE)

        assertEquals(listOf(2L, 1L, 3L), result.map { it.image.id })
    }

    @Test
    fun `by uncertainty puts smallest margin first among assigned`() {
        val input = listOf(
            suggestion(1L, score = 0.9f, secondBestScore = 0.2f),
            suggestion(2L, score = 0.8f, secondBestScore = 0.75f),
            suggestion(3L, score = 0.85f, secondBestScore = 0.5f)
        )

        val result = useCase(input, threshold = 0.6f, sortMode = SuggestionSortMode.BY_UNCERTAINTY)

        assertEquals(listOf(2L, 3L, 1L), result.map { it.image.id })
    }

    @Test
    fun `by uncertainty keeps unassigned at the end`() {
        val input = listOf(
            suggestion(1L, score = 0.4f, secondBestScore = 0.39f, destinationId = 0L),
            suggestion(2L, score = 0.9f, secondBestScore = 0.2f)
        )

        val result = useCase(input, threshold = 0.6f, sortMode = SuggestionSortMode.BY_UNCERTAINTY)

        assertEquals(listOf(2L, 1L), result.map { it.image.id })
    }

    @Test
    fun `default sort mode is by score`() {
        val input = listOf(
            suggestion(1L, score = 0.7f, secondBestScore = 0.69f),
            suggestion(2L, score = 0.9f, secondBestScore = 0.2f)
        )

        val result = useCase(input, threshold = 0.6f)

        assertEquals(listOf(2L, 1L), result.map { it.image.id })
    }
}
