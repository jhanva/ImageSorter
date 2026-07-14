package com.smartfolder.domain.model

data class StoredSuggestion(
    val imageId: Long,
    val destinationFolderId: Long,
    val score: Float,
    val secondBestScore: Float,
    val centroidScore: Float,
    val topKScore: Float,
    val topSimilarIds: List<Long>,
    val topSimilarScores: List<Float>,
    val candidateIds: List<Long> = emptyList(),
    val candidateScores: List<Float> = emptyList(),
    val createdAt: Long,
    val reviewStatus: ReviewStatus = ReviewStatus.PENDING
)
