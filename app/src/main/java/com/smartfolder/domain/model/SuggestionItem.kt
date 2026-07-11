package com.smartfolder.domain.model

data class SuggestionItem(
    val image: ImageInfo,
    val suggestedDestinationId: Long,
    val score: Float,
    val secondBestScore: Float,
    val centroidScore: Float,
    val topKScore: Float,
    val topSimilarImages: List<SimilarMatch>,
    val candidateIds: List<Long> = emptyList(),
    val candidateScores: List<Float> = emptyList()
)

val SuggestionItem.confidenceMargin: Float
    get() = score - secondBestScore
