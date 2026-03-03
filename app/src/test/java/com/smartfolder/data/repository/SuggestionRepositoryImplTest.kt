package com.smartfolder.data.repository

import com.smartfolder.data.local.db.dao.SuggestionDao
import com.smartfolder.data.local.db.entities.SuggestionEntity
import com.smartfolder.domain.model.StoredSuggestion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionRepositoryImplTest {

    @Test
    fun `replaceAll and getAll roundtrip preserves fields`() = runTest {
        val dao = FakeSuggestionDao()
        val repo = SuggestionRepositoryImpl(dao)

        val input = listOf(
            StoredSuggestion(
                imageId = 1L,
                score = 0.9f,
                centroidScore = 0.8f,
                topKScore = 0.95f,
                topSimilarIds = listOf(2L, 3L),
                topSimilarScores = listOf(0.98f, 0.96f),
                createdAt = 123L
            ),
            StoredSuggestion(
                imageId = 4L,
                score = 0.7f,
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
        assertEquals(input[0].imageId, output[0].imageId)
        assertEquals(input[0].topSimilarIds, output[0].topSimilarIds)
        assertEquals(input[0].topSimilarScores, output[0].topSimilarScores)
        assertEquals(input[1].topSimilarIds, output[1].topSimilarIds)
        assertEquals(input[1].topSimilarScores, output[1].topSimilarScores)
    }

    private class FakeSuggestionDao : SuggestionDao {
        private val data = mutableListOf<SuggestionEntity>()

        override suspend fun getAll(): List<SuggestionEntity> = data.toList()

        override suspend fun insertAll(suggestions: List<SuggestionEntity>) {
            data.addAll(suggestions)
        }

        override suspend fun deleteAll() {
            data.clear()
        }
    }
}
