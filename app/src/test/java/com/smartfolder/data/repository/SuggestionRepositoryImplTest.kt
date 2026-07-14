package com.smartfolder.data.repository

import com.smartfolder.data.local.db.dao.SuggestionDao
import com.smartfolder.data.local.db.entities.SuggestionEntity
import com.smartfolder.domain.model.ReviewStatus
import com.smartfolder.domain.model.StoredSuggestion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionRepositoryImplTest {

    @Test
    fun `replaceAll and getAll roundtrip preserves destination fields`() = runTest {
        val dao = FakeSuggestionDao()
        val repo = SuggestionRepositoryImpl(dao)

        val input = listOf(
            StoredSuggestion(
                imageId = 1L,
                destinationFolderId = 100L,
                score = 0.9f,
                secondBestScore = 0.7f,
                centroidScore = 0.8f,
                topKScore = 0.95f,
                topSimilarIds = listOf(2L, 3L),
                topSimilarScores = listOf(0.98f, 0.96f),
                candidateIds = listOf(100L, 101L),
                candidateScores = listOf(0.9f, 0.55f),
                createdAt = 123L
            ),
            StoredSuggestion(
                imageId = 4L,
                destinationFolderId = 101L,
                score = 0.7f,
                secondBestScore = 0.4f,
                centroidScore = 0.6f,
                topKScore = 0.8f,
                topSimilarIds = emptyList(),
                topSimilarScores = emptyList(),
                createdAt = 456L
            )
        )

        repo.replaceAll(input)
        val output = repo.getAll()

        assertEquals(2, output.size)
        assertEquals(input[0].destinationFolderId, output[0].destinationFolderId)
        assertEquals(input[0].secondBestScore, output[0].secondBestScore, 0.0001f)
        assertEquals(input[0].topSimilarIds, output[0].topSimilarIds)
        assertEquals(input[0].candidateIds, output[0].candidateIds)
        assertEquals(input[0].candidateScores, output[0].candidateScores)
        assertEquals(input[1].candidateIds, output[1].candidateIds)
        assertEquals(input[1].destinationFolderId, output[1].destinationFolderId)
        assertEquals(input[1].secondBestScore, output[1].secondBestScore, 0.0001f)
    }

    @Test
    fun `roundtrip preserves review status`() = runTest {
        val dao = FakeSuggestionDao()
        val repo = SuggestionRepositoryImpl(dao)

        repo.replaceAll(
            listOf(
                storedSuggestion(imageId = 1L, reviewStatus = ReviewStatus.ACCEPTED),
                storedSuggestion(imageId = 2L, reviewStatus = ReviewStatus.SKIPPED),
                storedSuggestion(imageId = 3L)
            )
        )
        val output = repo.getAll().associateBy { it.imageId }

        assertEquals(ReviewStatus.ACCEPTED, output[1L]?.reviewStatus)
        assertEquals(ReviewStatus.SKIPPED, output[2L]?.reviewStatus)
        assertEquals(ReviewStatus.PENDING, output[3L]?.reviewStatus)
    }

    @Test
    fun `setReviewStatus updates status and destination`() = runTest {
        val dao = FakeSuggestionDao()
        val repo = SuggestionRepositoryImpl(dao)
        repo.replaceAll(listOf(storedSuggestion(imageId = 1L, destinationFolderId = 100L)))

        repo.setReviewStatus(imageId = 1L, status = ReviewStatus.ACCEPTED, destinationFolderId = 200L)

        val updated = repo.getAll().single()
        assertEquals(ReviewStatus.ACCEPTED, updated.reviewStatus)
        assertEquals(200L, updated.destinationFolderId)
    }

    @Test
    fun `setReviewStatus without destination keeps existing destination`() = runTest {
        val dao = FakeSuggestionDao()
        val repo = SuggestionRepositoryImpl(dao)
        repo.replaceAll(listOf(storedSuggestion(imageId = 1L, destinationFolderId = 100L)))

        repo.setReviewStatus(imageId = 1L, status = ReviewStatus.SKIPPED, destinationFolderId = null)

        val updated = repo.getAll().single()
        assertEquals(ReviewStatus.SKIPPED, updated.reviewStatus)
        assertEquals(100L, updated.destinationFolderId)
    }

    private fun storedSuggestion(
        imageId: Long,
        destinationFolderId: Long = 100L,
        reviewStatus: ReviewStatus = ReviewStatus.PENDING
    ) = StoredSuggestion(
        imageId = imageId,
        destinationFolderId = destinationFolderId,
        score = 0.9f,
        secondBestScore = 0.7f,
        centroidScore = 0.8f,
        topKScore = 0.95f,
        topSimilarIds = emptyList(),
        topSimilarScores = emptyList(),
        createdAt = 123L,
        reviewStatus = reviewStatus
    )

    private class FakeSuggestionDao : SuggestionDao {
        private val data = mutableListOf<SuggestionEntity>()

        override suspend fun getAll(): List<SuggestionEntity> = data.toList()

        override suspend fun insertAll(suggestions: List<SuggestionEntity>) {
            data.addAll(suggestions)
        }

        override suspend fun deleteAll() {
            data.clear()
        }

        override suspend fun updateReviewStatus(imageId: Long, status: String) {
            data.replaceAll { entity ->
                if (entity.imageId == imageId) entity.copy(reviewStatus = status) else entity
            }
        }

        override suspend fun updateReviewStatusAndDestination(
            imageId: Long,
            status: String,
            destinationFolderId: Long
        ) {
            data.replaceAll { entity ->
                if (entity.imageId == imageId) {
                    entity.copy(reviewStatus = status, destinationFolderId = destinationFolderId)
                } else {
                    entity
                }
            }
        }
    }
}
