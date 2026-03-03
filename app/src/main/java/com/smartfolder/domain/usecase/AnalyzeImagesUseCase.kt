package com.smartfolder.domain.usecase

import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.domain.model.AnalysisProgress
import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.model.SimilarMatch
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.repository.EmbeddingRepository
import com.smartfolder.domain.repository.ImageRepository
import com.smartfolder.domain.repository.SuggestionRepository
import com.smartfolder.ml.CentroidCalculator
import com.smartfolder.ml.SimilarityCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class AnalyzeImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val suggestionRepository: SuggestionRepository
) {
    companion object {
        // Throttle progress emissions: emit at most every N images
        private const val PROGRESS_EMIT_INTERVAL = 50
        private const val MAX_ANALYSIS_WORKERS = 6
        private const val PROGRESS_EMIT_INTERVAL_MS = 250L
    }

    data class Result(
        val suggestions: List<SuggestionItem>,
        val progress: AnalysisProgress
    )

    private data class Candidate(
        val imageId: Long,
        val score: Float,
        val centroidScore: Float,
        val topKScore: Float,
        val topSimilarIds: List<Long>,
        val topSimilarScores: List<Float>
    )

    operator fun invoke(
        referenceFolder: Folder,
        unsortedFolder: Folder,
        modelChoice: ModelChoice,
        threshold: Float,
        topK: Int = 5,
        executionProfile: ExecutionProfile = ExecutionProfile.BALANCED
    ): Flow<Result> = flow {
        try {
            emit(Result(emptyList(), AnalysisProgress(phase = AnalysisPhase.CENTROID)))
            suggestionRepository.deleteAll()

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

            val safeTopK = topK.coerceAtLeast(1)
            val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val workerCount = resolveAnalysisWorkers(executionProfile, cpuCount)
            val chunkSize = (total / (workerCount * 4)).coerceAtLeast(PROGRESS_EMIT_INTERVAL)
            val chunks = unsortedEmbeddings.chunked(chunkSize)

            val chunkResults = coroutineScope {
                chunks.mapIndexed { chunkIndex, chunk ->
                    async(Dispatchers.Default) {
                        val localCandidates = mutableListOf<Candidate>()
                        for (unsortedEmb in chunk) {
                            val centroidScore = SimilarityCalculator.cosineSimilarity(unsortedEmb.vector, centroid)
                            if (centroidScore < threshold * 0.5f) continue

                            val topKSimilarities = mutableListOf<Pair<Long, Float>>()
                            var minInTopK = Float.MIN_VALUE

                            for ((imageId, refVector) in refImageVectors) {
                                val sim = SimilarityCalculator.cosineSimilarity(unsortedEmb.vector, refVector)
                                if (topKSimilarities.size < safeTopK) {
                                    topKSimilarities.add(imageId to sim)
                                    if (topKSimilarities.size == safeTopK) {
                                        minInTopK = topKSimilarities.minOf { it.second }
                                    }
                                } else if (sim > minInTopK) {
                                    val minIdx = topKSimilarities.indexOfFirst { it.second == minInTopK }
                                    topKSimilarities[minIdx] = imageId to sim
                                    minInTopK = topKSimilarities.minOf { it.second }
                                }
                            }
                            topKSimilarities.sortByDescending { it.second }

                            val topKScore = if (topKSimilarities.isNotEmpty()) {
                                topKSimilarities.map { it.second }.average().toFloat()
                            } else {
                                0f
                            }
                            val topKMax = if (topKSimilarities.isNotEmpty()) {
                                topKSimilarities.maxOf { it.second }
                            } else {
                                0f
                            }
                            val combinedScore = SimilarityCalculator.computeScore(centroidScore, topKScore, topKMax)
                            if (combinedScore < threshold) continue

                            localCandidates.add(
                                Candidate(
                                    imageId = unsortedEmb.imageId,
                                    score = combinedScore,
                                    centroidScore = centroidScore,
                                    topKScore = topKScore,
                                    topSimilarIds = topKSimilarities.map { it.first },
                                    topSimilarScores = topKSimilarities.map { it.second }
                                )
                            )
                        }
                        chunkIndex to localCandidates
                    }
                }.awaitAll().sortedBy { it.first }
            }

            var processed = 0
            val candidates = mutableListOf<Candidate>()
            var lastProgressEmitAt = 0L
            chunkResults.forEach { (_, chunkCandidates) ->
                candidates.addAll(chunkCandidates)
                processed = (processed + chunkSize).coerceAtMost(total)
                val now = System.currentTimeMillis()
                if (processed == total || (now - lastProgressEmitAt) >= PROGRESS_EMIT_INTERVAL_MS) {
                    emit(Result(emptyList(), AnalysisProgress(
                        phase = AnalysisPhase.COMPARING,
                        current = processed,
                        total = total
                    )))
                    lastProgressEmitAt = now
                }
            }

            val allIds = buildSet {
                candidates.forEach { candidate ->
                    add(candidate.imageId)
                    candidate.topSimilarIds.forEach { add(it) }
                }
            }.toList()
            val imagesById = imageRepository.getByIds(allIds).associateBy { it.id }

            val suggestions = candidates.mapNotNull { candidate ->
                val unsortedImage = imagesById[candidate.imageId] ?: return@mapNotNull null
                val topSimilar = candidate.topSimilarIds.zip(candidate.topSimilarScores).mapNotNull { (id, score) ->
                    imagesById[id]?.let { SimilarMatch(image = it, score = score) }
                }
                SuggestionItem(
                    image = unsortedImage,
                    score = candidate.score,
                    centroidScore = candidate.centroidScore,
                    topKScore = candidate.topKScore,
                    topSimilarFromA = topSimilar
                )
            }

            // Sort by score descending
            val sortedSuggestions = suggestions.sortedByDescending { it.score }

            val createdAt = System.currentTimeMillis()
            val stored = sortedSuggestions.map { suggestion ->
                StoredSuggestion(
                    imageId = suggestion.image.id,
                    score = suggestion.score,
                    centroidScore = suggestion.centroidScore,
                    topKScore = suggestion.topKScore,
                    topSimilarIds = suggestion.topSimilarFromA.map { it.image.id },
                    topSimilarScores = suggestion.topSimilarFromA.map { it.score },
                    createdAt = createdAt
                )
            }
            suggestionRepository.replaceAll(stored)

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
    }.flowOn(Dispatchers.Default)

    private fun resolveAnalysisWorkers(profile: ExecutionProfile, cpuCount: Int): Int {
        return when (profile) {
            ExecutionProfile.BATTERY -> 1
            ExecutionProfile.BALANCED -> (cpuCount / 3).coerceIn(1, 3)
            ExecutionProfile.PERFORMANCE -> (cpuCount / 2).coerceIn(2, MAX_ANALYSIS_WORKERS)
        }
    }
}
