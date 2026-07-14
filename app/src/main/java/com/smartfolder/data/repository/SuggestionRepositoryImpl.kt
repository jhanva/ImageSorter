package com.smartfolder.data.repository

import com.smartfolder.data.local.db.dao.SuggestionDao
import com.smartfolder.data.local.db.entities.SuggestionEntity
import com.smartfolder.domain.model.ReviewStatus
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

    override suspend fun insertAll(suggestions: List<StoredSuggestion>) {
        if (suggestions.isNotEmpty()) {
            suggestionDao.insertAll(suggestions.map { it.toEntity() })
        }
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

    override suspend fun setReviewStatus(imageId: Long, status: ReviewStatus, destinationFolderId: Long?) {
        if (destinationFolderId != null) {
            suggestionDao.updateReviewStatusAndDestination(imageId, status.name, destinationFolderId)
        } else {
            suggestionDao.updateReviewStatus(imageId, status.name)
        }
    }

    private fun SuggestionEntity.toDomain(): StoredSuggestion = StoredSuggestion(
        imageId = imageId,
        destinationFolderId = destinationFolderId,
        score = score,
        secondBestScore = secondBestScore,
        centroidScore = centroidScore,
        topKScore = topKScore,
        topSimilarIds = parseLongList(topSimilarIds),
        topSimilarScores = parseFloatList(topSimilarScores),
        candidateIds = parseLongList(candidateIds),
        candidateScores = parseFloatList(candidateScores),
        createdAt = createdAt,
        reviewStatus = ReviewStatus.fromName(reviewStatus)
    )

    private fun StoredSuggestion.toEntity(): SuggestionEntity = SuggestionEntity(
        imageId = imageId,
        destinationFolderId = destinationFolderId,
        score = score,
        secondBestScore = secondBestScore,
        centroidScore = centroidScore,
        topKScore = topKScore,
        topSimilarIds = topSimilarIds.joinToString(","),
        topSimilarScores = topSimilarScores.joinToString(","),
        candidateIds = candidateIds.joinToString(","),
        candidateScores = candidateScores.joinToString(","),
        createdAt = createdAt,
        reviewStatus = reviewStatus.name
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
