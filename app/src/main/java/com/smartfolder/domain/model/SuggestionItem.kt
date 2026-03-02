package com.smartfolder.domain.model

data class SuggestionItem(
    val image: ImageInfo,
    val score: Float,
    val centroidScore: Float,
    val topKScore: Float,
    val topSimilarFromA: List<SimilarMatch>
)
