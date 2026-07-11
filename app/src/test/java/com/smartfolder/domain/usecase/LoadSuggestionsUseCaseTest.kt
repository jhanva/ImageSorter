package com.smartfolder.domain.usecase

import android.net.Uri
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.SuggestionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class LoadSuggestionsUseCaseTest {

    private lateinit var suggestionRepository: SuggestionRepository
    private lateinit var imageRepository: ImageRepository
    private lateinit var useCase: LoadSuggestionsUseCase

    @Before
    fun setup() {
        suggestionRepository = mock(SuggestionRepository::class.java)
        imageRepository = mock(ImageRepository::class.java)
        useCase = LoadSuggestionsUseCase(suggestionRepository, imageRepository)
    }

    @Test
    fun `loads suggestions with similar matches`() = runTest {
        val stored = listOf(
            StoredSuggestion(
                imageId = 10L,
                destinationFolderId = 1L,
                score = 0.9f,
                secondBestScore = 0.7f,
                centroidScore = 0.8f,
                topKScore = 0.95f,
                topSimilarIds = listOf(1L, 2L),
                topSimilarScores = listOf(0.98f, 0.96f),
                createdAt = 123L
            )
        )

        `when`(suggestionRepository.getAll()).thenReturn(stored)

        val img10 = ImageInfo(10L, 1L, mock(Uri::class.java), "main.jpg", "h1", 10L, 1L)
        val img1 = ImageInfo(1L, 1L, mock(Uri::class.java), "a.jpg", "h2", 10L, 1L)
        val img2 = ImageInfo(2L, 1L, mock(Uri::class.java), "b.jpg", "h3", 10L, 1L)
        `when`(imageRepository.getByIds(listOf(10L, 1L, 2L))).thenReturn(listOf(img10, img1, img2))

        val results = useCase()

        assertEquals(1, results.size)
        assertEquals(10L, results[0].image.id)
        assertEquals(1L, results[0].suggestedDestinationId)
        assertEquals(2, results[0].topSimilarImages.size)
        assertEquals(0.98f, results[0].topSimilarImages[0].score, 0.0001f)
    }

    @Test
    fun `passes candidate destinations through to the suggestion item`() = runTest {
        val stored = listOf(
            StoredSuggestion(
                imageId = 10L,
                destinationFolderId = 0L,
                score = 0.4f,
                secondBestScore = 0.3f,
                centroidScore = 0.4f,
                topKScore = 0.4f,
                topSimilarIds = emptyList(),
                topSimilarScores = emptyList(),
                candidateIds = listOf(7L, 8L),
                candidateScores = listOf(0.4f, 0.3f),
                createdAt = 123L
            )
        )
        `when`(suggestionRepository.getAll()).thenReturn(stored)
        val img10 = ImageInfo(10L, 1L, mock(Uri::class.java), "main.jpg", "h1", 10L, 1L)
        `when`(imageRepository.getByIds(listOf(10L))).thenReturn(listOf(img10))

        val results = useCase()

        assertEquals(listOf(7L, 8L), results.single().candidateIds)
        assertEquals(listOf(0.4f, 0.3f), results.single().candidateScores)
    }

    @Test
    fun `drops suggestions when main image missing`() = runTest {
        val stored = listOf(
            StoredSuggestion(
                imageId = 99L,
                destinationFolderId = 1L,
                score = 0.9f,
                secondBestScore = 0.7f,
                centroidScore = 0.8f,
                topKScore = 0.95f,
                topSimilarIds = listOf(1L),
                topSimilarScores = listOf(0.98f),
                createdAt = 123L
            )
        )
        `when`(suggestionRepository.getAll()).thenReturn(stored)
        `when`(imageRepository.getByIds(listOf(99L, 1L))).thenReturn(emptyList())

        val results = useCase()

        assertTrue(results.isEmpty())
    }
}
