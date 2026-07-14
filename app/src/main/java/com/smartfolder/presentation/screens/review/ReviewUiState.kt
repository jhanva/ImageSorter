package com.smartfolder.presentation.screens.review

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.SuggestionItem

data class CandidateDestination(
    val folder: Folder,
    val score: Float
)

data class ReviewMoveSummary(
    val moved: Int,
    val copiedOnly: Int,
    val failed: Int
)

data class ReviewUiState(
    val queue: List<SuggestionItem> = emptyList(),
    val currentIndex: Int = 0,
    val destinationFolders: List<Folder> = emptyList(),
    val acceptedCount: Int = 0,
    val skippedCount: Int = 0,
    val acceptedByDestination: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val isMoving: Boolean = false,
    val moveSummary: ReviewMoveSummary? = null,
    val error: String? = null
) {
    val current: SuggestionItem? get() = queue.getOrNull(currentIndex)

    val isComplete: Boolean get() = !isLoading && current == null

    val reviewedCount: Int get() = acceptedCount + skippedCount

    val totalCount: Int get() = queue.size

    val currentCandidates: List<CandidateDestination>
        get() {
            val item = current ?: return emptyList()
            val foldersById = destinationFolders.associateBy { it.id }
            val fromCandidates = item.candidateIds
                .zip(item.candidateScores)
                .mapNotNull { (id, score) ->
                    foldersById[id]?.let { CandidateDestination(it, score) }
                }
            if (fromCandidates.isNotEmpty()) return fromCandidates
            return foldersById[item.suggestedDestinationId]
                ?.let { listOf(CandidateDestination(it, item.score)) }
                ?: emptyList()
        }
}
