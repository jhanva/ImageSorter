package com.smartfolder.domain.model

data class StoredSuggestion(
    val imageId: Long,
    val score: Float,
    val centroidScore: Float,
    val topKScore: Float,
    val topSimilarIds: List<Long>,
    val topSimilarScores: List<Float>,
    val createdAt: Long
)
