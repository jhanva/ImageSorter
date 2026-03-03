package com.smartfolder.data.repository

import com.smartfolder.data.local.db.dao.SuggestionDao
import com.smartfolder.data.local.db.entities.SuggestionEntity
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.repository.SuggestionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionRepositoryImpl @Inject constructor(
    private val suggestionDao: SuggestionDao
) : SuggestionRepository {

    override suspend fun getAll(): List<StoredSuggestion> {
        return suggestionDao.getAll().map { it.toDomain() }
    }

    override suspend fun replaceAll(suggestions: List<StoredSuggestion>) {
        suggestionDao.deleteAll()
        if (suggestions.isNotEmpty()) {
            suggestionDao.insertAll(suggestions.map { it.toEntity() })
        }
    }

    override suspend fun deleteAll() {
        suggestionDao.deleteAll()
    }

    private fun SuggestionEntity.toDomain(): StoredSuggestion = StoredSuggestion(
        imageId = imageId,
        score = score,
        centroidScore = centroidScore,
        topKScore = topKScore,
        topSimilarIds = parseLongList(topSimilarIds),
        topSimilarScores = parseFloatList(topSimilarScores),
        createdAt = createdAt
    )

    private fun StoredSuggestion.toEntity(): SuggestionEntity = SuggestionEntity(
        imageId = imageId,
        score = score,
        centroidScore = centroidScore,
        topKScore = topKScore,
        topSimilarIds = topSimilarIds.joinToString(","),
        topSimilarScores = topSimilarScores.joinToString(","),
        createdAt = createdAt
    )

    private fun parseLongList(raw: String): List<Long> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.toLongOrNull() }
    }

    private fun parseFloatList(raw: String): List<Float> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.toFloatOrNull() }
    }
}
