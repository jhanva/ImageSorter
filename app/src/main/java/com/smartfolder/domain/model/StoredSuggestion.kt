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
    val createdAt: Long
)
