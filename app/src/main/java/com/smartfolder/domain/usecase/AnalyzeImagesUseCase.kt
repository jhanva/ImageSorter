package com.smartfolder.domain.usecase

import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.domain.model.AnalysisProgress
import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.model.SimilarMatch
import com.smartfolder.domain.model.StoredSuggestion
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.model.confidenceMargin
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
        private const val MAX_ANALYSIS_WORKERS = 6
        private const val PROGRESS_EMIT_INTERVAL_MS = 250L
    }

    data class Result(
        val suggestions: List<SuggestionItem>,
        val progress: AnalysisProgress
    )

    private data class DestinationIndex(
        val folderId: Long,
        val centroid: FloatArray,
        val imageVectors: Map<Long, FloatArray>
    )

    private data class DestinationCandidate(
        val destinationFolderId: Long,
        val score: Float,
        val centroidScore: Float,
        val topKScore: Float,
        val topSimilarIds: List<Long>,
        val topSimilarScores: List<Float>
    )

    private data class SourceSuggestion(
        val imageId: Long,
        val assignedDestinationId: Long,
        val best: DestinationCandidate?,
        val secondBestScore: Float
    )

    operator fun invoke(
        destinationFolders: List<Folder>,
        sourceFolders: List<Folder>,
        modelChoice: ModelChoice,
        threshold: Float,
        topK: Int = 5,
        executionProfile: ExecutionProfile = ExecutionProfile.BALANCED
    ): Flow<Result> = flow {
        try {
            emit(Result(emptyList(), AnalysisProgress(phase = AnalysisPhase.CENTROID)))
            suggestionRepository.deleteAll()

            if (destinationFolders.isEmpty()) {
                emit(Result(emptyList(), AnalysisProgress(
                    phase = AnalysisPhase.ERROR,
                    errorMessage = "At least one destination folder must be selected."
                )))
                return@flow
            }
            if (sourceFolders.isEmpty()) {
                emit(Result(emptyList(), AnalysisProgress(
                    phase = AnalysisPhase.ERROR,
                    errorMessage = "At least one source folder must be selected."
                )))
                return@flow
            }

            val destinationIndexes = destinationFolders.map { folder ->
                val embeddings = embeddingRepository.getByFolderAndModel(folder.id, modelChoice.modelFileName)
                if (embeddings.isEmpty()) {
                    emit(Result(emptyList(), AnalysisProgress(
                        phase = AnalysisPhase.ERROR,
                        errorMessage = "Destination folder '${folder.displayName}' is not indexed for ${modelChoice.displayName}."
                    )))
                    return@flow
                } else {
                    DestinationIndex(
                        folderId = folder.id,
                        centroid = CentroidCalculator.compute(embeddings.map { it.vector }),
                        imageVectors = embeddings.associate { it.imageId to it.vector }
                    )
                }
            }

            val sourceEmbeddings = sourceFolders.flatMap { folder ->
                val embeddings = embeddingRepository.getByFolderAndModel(folder.id, modelChoice.modelFileName)
                if (embeddings.isEmpty()) {
                    emit(Result(emptyList(), AnalysisProgress(
                        phase = AnalysisPhase.ERROR,
                        errorMessage = "Source folder '${folder.displayName}' is not indexed for ${modelChoice.displayName}."
                    )))
                    return@flow
                }
                embeddings
            }

            val total = sourceEmbeddings.size
            emit(Result(emptyList(), AnalysisProgress(
                phase = AnalysisPhase.COMPARING,
                total = total
            )))

            val safeTopK = topK.coerceAtLeast(1)
            val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val workerCount = resolveAnalysisWorkers(executionProfile, cpuCount)
            val chunkSize = (total / (workerCount * 4)).coerceAtLeast(1)
            val chunks = sourceEmbeddings.chunked(chunkSize)

            val chunkResults = coroutineScope {
                chunks.mapIndexed { chunkIndex, chunk ->
                    async(Dispatchers.Default) {
                        val localSuggestions = chunk.map { sourceEmbedding ->
                            rankSourceImage(
                                imageId = sourceEmbedding.imageId,
                                sourceVector = sourceEmbedding.vector,
                                destinations = destinationIndexes,
                                threshold = threshold,
                                topK = safeTopK
                            )
                        }
                        chunkIndex to localSuggestions
                    }
                }.awaitAll().sortedBy { it.first }
            }

            var processed = 0
            var lastProgressEmitAt = 0L
            val suggestions = mutableListOf<SourceSuggestion>()
            chunkResults.forEach { (_, chunkSuggestions) ->
                suggestions += chunkSuggestions
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
                suggestions.forEach { suggestion ->
                    add(suggestion.imageId)
                    suggestion.best?.topSimilarIds?.forEach { add(it) }
                }
            }.toList()
            val imagesById = imageRepository.getByIds(allIds).associateBy { it.id }

            val resolvedSuggestions = suggestions.mapNotNull { suggestion ->
                val sourceImage = imagesById[suggestion.imageId] ?: return@mapNotNull null
                val topSimilarImages = suggestion.best?.topSimilarIds
                    ?.zip(suggestion.best.topSimilarScores)
                    .orEmpty()
                    .mapNotNull { (id, score) ->
                        imagesById[id]?.let { SimilarMatch(image = it, score = score) }
                    }
                SuggestionItem(
                    image = sourceImage,
                    suggestedDestinationId = suggestion.assignedDestinationId,
                    score = suggestion.best?.score ?: 0f,
                    secondBestScore = suggestion.secondBestScore,
                    centroidScore = suggestion.best?.centroidScore ?: 0f,
                    topKScore = suggestion.best?.topKScore ?: 0f,
                    topSimilarImages = topSimilarImages
                )
            }.sortedWith(compareByDescending<SuggestionItem> { it.score }.thenByDescending { it.confidenceMargin })

            val createdAt = System.currentTimeMillis()
            val stored = resolvedSuggestions.map { suggestion ->
                StoredSuggestion(
                    imageId = suggestion.image.id,
                    destinationFolderId = suggestion.suggestedDestinationId,
                    score = suggestion.score,
                    secondBestScore = suggestion.secondBestScore,
                    centroidScore = suggestion.centroidScore,
                    topKScore = suggestion.topKScore,
                    topSimilarIds = suggestion.topSimilarImages.map { it.image.id },
                    topSimilarScores = suggestion.topSimilarImages.map { it.score },
                    createdAt = createdAt
                )
            }
            suggestionRepository.replaceAll(stored)

            emit(Result(resolvedSuggestions, AnalysisProgress(
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

    private fun rankSourceImage(
        imageId: Long,
        sourceVector: FloatArray,
        destinations: List<DestinationIndex>,
        threshold: Float,
        topK: Int
    ): SourceSuggestion {
        val rankedCandidates = destinations.mapNotNull { destination ->
            val centroidScore = SimilarityCalculator.cosineSimilarity(sourceVector, destination.centroid)
            if (centroidScore < threshold * 0.5f) {
                return@mapNotNull null
            }

            val topSimilarities = mutableListOf<Pair<Long, Float>>()
            var minInTopK = Float.MIN_VALUE
            destination.imageVectors.forEach { (destinationImageId, destinationVector) ->
                val similarity = SimilarityCalculator.cosineSimilarity(sourceVector, destinationVector)
                if (topSimilarities.size < topK) {
                    topSimilarities += destinationImageId to similarity
                    if (topSimilarities.size == topK) {
                        minInTopK = topSimilarities.minOf { it.second }
                    }
                } else if (similarity > minInTopK) {
                    val minIndex = topSimilarities.indexOfFirst { it.second == minInTopK }
                    topSimilarities[minIndex] = destinationImageId to similarity
                    minInTopK = topSimilarities.minOf { it.second }
                }
            }

            topSimilarities.sortByDescending { it.second }
            val topKScore = if (topSimilarities.isNotEmpty()) {
                topSimilarities.map { it.second }.average().toFloat()
            } else {
                0f
            }
            val topKMax = topSimilarities.maxOfOrNull { it.second } ?: 0f
            val referenceSupport = SimilarityCalculator.computeReferenceSupport(
                topSimilarities.map { it.second }
            )
            val combinedScore = SimilarityCalculator.computeScore(
                centroidScore = centroidScore,
                topKMean = topKScore,
                topKMax = topKMax,
                referenceSupport = referenceSupport
            )
            DestinationCandidate(
                destinationFolderId = destination.folderId,
                score = combinedScore,
                centroidScore = centroidScore,
                topKScore = topKScore,
                topSimilarIds = topSimilarities.map { it.first },
                topSimilarScores = topSimilarities.map { it.second }
            )
        }.sortedByDescending { it.score }

        val best = rankedCandidates.firstOrNull()
            ?: return SourceSuggestion(
                imageId = imageId,
                assignedDestinationId = 0L,
                best = null,
                secondBestScore = 0f
            )
        val secondBestScore = rankedCandidates.getOrNull(1)?.score ?: 0f

        return SourceSuggestion(
            imageId = imageId,
            assignedDestinationId = if (best.score >= threshold) best.destinationFolderId else 0L,
            best = best,
            secondBestScore = secondBestScore
        )
    }

    private fun resolveAnalysisWorkers(profile: ExecutionProfile, cpuCount: Int): Int {
        return when (profile) {
            ExecutionProfile.BATTERY -> 1
            ExecutionProfile.BALANCED -> (cpuCount / 3).coerceIn(1, 3)
            ExecutionProfile.PERFORMANCE -> (cpuCount / 2).coerceIn(2, MAX_ANALYSIS_WORKERS)
        }
    }
}
