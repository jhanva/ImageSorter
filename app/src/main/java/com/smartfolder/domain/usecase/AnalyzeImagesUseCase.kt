package com.smartfolder.domain.usecase

import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.domain.model.AnalysisProgress
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.model.SimilarMatch
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.ml.CentroidCalculator
import com.smartfolder.ml.SimilarityCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import javax.inject.Inject

class AnalyzeImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val embeddingRepository: EmbeddingRepository
) {
    companion object {
        // Throttle progress emissions: emit at most every N images
        private const val PROGRESS_EMIT_INTERVAL = 50
    }

    data class Result(
        val suggestions: List<SuggestionItem>,
        val progress: AnalysisProgress
    )

    operator fun invoke(
        referenceFolder: Folder,
        unsortedFolder: Folder,
        modelChoice: ModelChoice,
        threshold: Float,
        topK: Int = 3
    ): Flow<Result> = flow {
        try {
            emit(Result(emptyList(), AnalysisProgress(phase = AnalysisPhase.CENTROID)))

            // Get reference embeddings
            val refEmbeddings = embeddingRepository.getByFolderAndModel(
                referenceFolder.id, modelChoice.modelFileName
            )
            if (refEmbeddings.isEmpty()) {
                emit(Result(emptyList(), AnalysisProgress(
                    phase = AnalysisPhase.ERROR,
                    errorMessage = "No embeddings found for reference folder. Please index first."
                )))
                return@flow
            }

            // Compute centroid
            val refVectors = refEmbeddings.map { it.vector }
            val centroid = CentroidCalculator.compute(refVectors)

            // Get unsorted embeddings
            val unsortedEmbeddings = embeddingRepository.getByFolderAndModel(
                unsortedFolder.id, modelChoice.modelFileName
            )
            if (unsortedEmbeddings.isEmpty()) {
                emit(Result(emptyList(), AnalysisProgress(
                    phase = AnalysisPhase.ERROR,
                    errorMessage = "No embeddings found for unsorted folder. Please index first."
                )))
                return@flow
            }

            // Build imageId -> vector map for reference
            val refImageVectors = refEmbeddings.associate { it.imageId to it.vector }

            val total = unsortedEmbeddings.size
            emit(Result(emptyList(), AnalysisProgress(
                phase = AnalysisPhase.COMPARING,
                total = total
            )))

            val suggestions = mutableListOf<SuggestionItem>()
            // Pre-cache image lookups for matched images to avoid repeated DB queries
            val imageCache = mutableMapOf<Long, ImageInfo?>()

            for ((index, unsortedEmb) in unsortedEmbeddings.withIndex()) {
                // Yield periodically for coroutine cooperativeness
                if (index % PROGRESS_EMIT_INTERVAL == 0) {
                    yield()
                }

                // Prefilter: check centroid similarity
                val centroidScore = SimilarityCalculator.cosineSimilarity(unsortedEmb.vector, centroid)
                if (centroidScore < threshold * 0.8f) {
                    // Throttle progress: only emit every N images or on last item
                    if (index % PROGRESS_EMIT_INTERVAL == 0 || index == total - 1) {
                        emit(Result(suggestions.toList(), AnalysisProgress(
                            phase = AnalysisPhase.COMPARING,
                            current = index + 1,
                            total = total
                        )))
                    }
                    continue
                }

                // Partial kNN: find top-K without full sort using a min-heap approach
                val topKSimilarities = mutableListOf<Pair<Long, Float>>()
                var minInTopK = Float.MIN_VALUE

                for ((imageId, refVector) in refImageVectors) {
                    val sim = SimilarityCalculator.cosineSimilarity(unsortedEmb.vector, refVector)
                    if (topKSimilarities.size < topK) {
                        topKSimilarities.add(imageId to sim)
                        if (topKSimilarities.size == topK) {
                            minInTopK = topKSimilarities.minOf { it.second }
                        }
                    } else if (sim > minInTopK) {
                        val minIdx = topKSimilarities.indexOfFirst { it.second == minInTopK }
                        topKSimilarities[minIdx] = imageId to sim
                        minInTopK = topKSimilarities.minOf { it.second }
                    }
                }
                topKSimilarities.sortByDescending { it.second }

                // Top-K score
                val topKScore = if (topKSimilarities.isNotEmpty()) {
                    topKSimilarities.map { it.second }.average().toFloat()
                } else {
                    0f
                }

                val combinedScore = SimilarityCalculator.computeScore(centroidScore, topKScore)

                if (combinedScore >= threshold) {
                    val unsortedImage = imageCache.getOrPut(unsortedEmb.imageId) {
                        imageRepository.getById(unsortedEmb.imageId)
                    } ?: continue

                    val topSimilar = topKSimilarities.mapNotNull { (imageId, score) ->
                        val image = imageCache.getOrPut(imageId) {
                            imageRepository.getById(imageId)
                        } ?: return@mapNotNull null
                        SimilarMatch(image = image, score = score)
                    }

                    suggestions.add(
                        SuggestionItem(
                            image = unsortedImage,
                            score = combinedScore,
                            centroidScore = centroidScore,
                            topKScore = topKScore,
                            topSimilarFromA = topSimilar
                        )
                    )
                }

                // Throttle progress: only emit every N images or on last item
                if (index % PROGRESS_EMIT_INTERVAL == 0 || index == total - 1) {
                    emit(Result(suggestions.toList(), AnalysisProgress(
                        phase = AnalysisPhase.COMPARING,
                        current = index + 1,
                        total = total
                    )))
                }
            }

            // Sort by score descending
            val sortedSuggestions = suggestions.sortedByDescending { it.score }

            emit(Result(sortedSuggestions, AnalysisProgress(
                phase = AnalysisPhase.COMPLETE,
                current = total,
                total = total
            )))
        } catch (e: Exception) {
            emit(Result(emptyList(), AnalysisProgress(
                phase = AnalysisPhase.ERROR,
                errorMessage = e.message ?: "Unknown error during analysis"
            )))
        }
    }
}
