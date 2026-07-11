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
import kotlinx.coroutines.yield
import javax.inject.Inject

class AnalyzeImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val suggestionRepository: SuggestionRepository
) {
    companion object {
        private const val MAX_ANALYSIS_WORKERS = 6
        private const val SUGGESTION_BATCH_SIZE = 500
        private const val MAX_STORED_CANDIDATES = 3
    }

    data class Result(
        val suggestions: List<SuggestionItem>,
        val progress: AnalysisProgress
    )

    private class DestinationMatrix(
        val folderId: Long,
        val centroid: FloatArray,
        val imageIds: LongArray,
        val vectors: FloatArray,
        val dim: Int,
        val count: Int
    )

    private data class DestinationCandidate(
        val destinationFolderId: Long,
        val score: Float,
        val centroidScore: Float,
        val topKScore: Float,
        val topSimilarIds: LongArray,
        val topSimilarScores: FloatArray,
        val topKSize: Int
    )

    private data class SourceSuggestion(
        val imageId: Long,
        val assignedDestinationId: Long,
        val best: DestinationCandidate?,
        val secondBestScore: Float,
        val candidateIds: List<Long> = emptyList(),
        val candidateScores: List<Float> = emptyList()
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

            val destinationMatrices = destinationFolders.map { folder ->
                val embeddings = embeddingRepository.getByFolderAndModel(folder.id, modelChoice.modelFileName)
                if (embeddings.isEmpty()) {
                    emit(Result(emptyList(), AnalysisProgress(
                        phase = AnalysisPhase.ERROR,
                        errorMessage = "Destination folder '${folder.displayName}' is not indexed for ${modelChoice.displayName}."
                    )))
                    return@flow
                }
                val dim = embeddings.first().vector.size
                val count = embeddings.size
                val ids = LongArray(count)
                val matrix = FloatArray(count * dim)
                val vectors = mutableListOf<FloatArray>()
                embeddings.forEachIndexed { i, emb ->
                    ids[i] = emb.imageId
                    emb.vector.copyInto(matrix, i * dim)
                    vectors.add(emb.vector)
                }
                DestinationMatrix(
                    folderId = folder.id,
                    centroid = CentroidCalculator.compute(vectors),
                    imageIds = ids,
                    vectors = matrix,
                    dim = dim,
                    count = count
                )
            }

            val totalSource = sourceFolders.sumOf { folder ->
                embeddingRepository.countByFolderAndModel(folder.id, modelChoice.modelFileName)
            }
            if (totalSource == 0) {
                val firstName = sourceFolders.first().displayName
                emit(Result(emptyList(), AnalysisProgress(
                    phase = AnalysisPhase.ERROR,
                    errorMessage = "Source folder '$firstName' is not indexed for ${modelChoice.displayName}."
                )))
                return@flow
            }

            suggestionRepository.deleteAll()

            emit(Result(emptyList(), AnalysisProgress(
                phase = AnalysisPhase.COMPARING,
                total = totalSource
            )))

            val safeTopK = topK.coerceAtLeast(1)
            val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val workerCount = resolveAnalysisWorkers(executionProfile, cpuCount)

            var globalProcessed = 0
            val pendingSuggestions = mutableListOf<SourceSuggestion>()
            val createdAt = System.currentTimeMillis()

            for (sourceFolder in sourceFolders) {
                val sourceEmbeddings = embeddingRepository.getByFolderAndModel(
                    sourceFolder.id, modelChoice.modelFileName
                )
                if (sourceEmbeddings.isEmpty()) {
                    emit(Result(emptyList(), AnalysisProgress(
                        phase = AnalysisPhase.ERROR,
                        errorMessage = "Source folder '${sourceFolder.displayName}' is not indexed for ${modelChoice.displayName}."
                    )))
                    return@flow
                }

                val chunkSize = (sourceEmbeddings.size / (workerCount * 2)).coerceIn(1, 200)
                val chunks = sourceEmbeddings.chunked(chunkSize)

                for (wave in chunks.chunked(workerCount)) {
                    val waveResults = coroutineScope {
                        wave.map { chunk ->
                            async(Dispatchers.Default) {
                                chunk.mapIndexed { i, sourceEmbedding ->
                                    if (i % 50 == 49) yield()
                                    rankSourceImage(
                                        imageId = sourceEmbedding.imageId,
                                        sourceVector = sourceEmbedding.vector,
                                        destinations = destinationMatrices,
                                        threshold = threshold,
                                        topK = safeTopK
                                    )
                                }
                            }
                        }.awaitAll()
                    }

                    for (chunkResult in waveResults) {
                        pendingSuggestions += chunkResult
                        globalProcessed = (globalProcessed + chunkResult.size).coerceAtMost(totalSource)
                    }

                    if (pendingSuggestions.size >= SUGGESTION_BATCH_SIZE) {
                        flushSuggestions(pendingSuggestions, createdAt)
                        pendingSuggestions.clear()
                    }

                    yield()
                    emit(Result(emptyList(), AnalysisProgress(
                        phase = AnalysisPhase.COMPARING,
                        current = globalProcessed,
                        total = totalSource
                    )))
                }
            }

            if (pendingSuggestions.isNotEmpty()) {
                flushSuggestions(pendingSuggestions, createdAt)
                pendingSuggestions.clear()
            }

            val storedSuggestions = suggestionRepository.getAll()
            val allIds = buildSet {
                storedSuggestions.forEach { s ->
                    add(s.imageId)
                    s.topSimilarIds.forEach { add(it) }
                }
            }.toList()
            val imagesById = imageRepository.getByIds(allIds).associateBy { it.id }

            val resolvedSuggestions = storedSuggestions.mapNotNull { stored ->
                val sourceImage = imagesById[stored.imageId] ?: return@mapNotNull null
                val topSimilarImages = stored.topSimilarIds
                    .zip(stored.topSimilarScores)
                    .mapNotNull { (id, score) ->
                        imagesById[id]?.let { SimilarMatch(image = it, score = score) }
                    }
                SuggestionItem(
                    image = sourceImage,
                    suggestedDestinationId = stored.destinationFolderId,
                    score = stored.score,
                    secondBestScore = stored.secondBestScore,
                    centroidScore = stored.centroidScore,
                    topKScore = stored.topKScore,
                    topSimilarImages = topSimilarImages,
                    candidateIds = stored.candidateIds,
                    candidateScores = stored.candidateScores
                )
            }.sortedWith(compareByDescending<SuggestionItem> { it.score }.thenByDescending { it.confidenceMargin })

            emit(Result(resolvedSuggestions, AnalysisProgress(
                phase = AnalysisPhase.COMPLETE,
                current = totalSource,
                total = totalSource
            )))
        } catch (e: Exception) {
            emit(Result(emptyList(), AnalysisProgress(
                phase = AnalysisPhase.ERROR,
                errorMessage = e.message ?: "Unknown error during analysis"
            )))
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun flushSuggestions(suggestions: List<SourceSuggestion>, createdAt: Long) {
        val stored = suggestions.map { suggestion ->
            StoredSuggestion(
                imageId = suggestion.imageId,
                destinationFolderId = suggestion.assignedDestinationId,
                score = suggestion.best?.score ?: 0f,
                secondBestScore = suggestion.secondBestScore,
                centroidScore = suggestion.best?.centroidScore ?: 0f,
                topKScore = suggestion.best?.topKScore ?: 0f,
                topSimilarIds = suggestion.best?.let { b ->
                    (0 until b.topKSize).map { b.topSimilarIds[it] }
                } ?: emptyList(),
                topSimilarScores = suggestion.best?.let { b ->
                    (0 until b.topKSize).map { b.topSimilarScores[it] }
                } ?: emptyList(),
                candidateIds = suggestion.candidateIds,
                candidateScores = suggestion.candidateScores,
                createdAt = createdAt
            )
        }
        suggestionRepository.insertAll(stored)
    }

    private fun rankSourceImage(
        imageId: Long,
        sourceVector: FloatArray,
        destinations: List<DestinationMatrix>,
        threshold: Float,
        topK: Int
    ): SourceSuggestion {
        val centroidCutoff = threshold * 0.6f
        val rankedCandidates = destinations.mapNotNull { destination ->
            val centroidScore = SimilarityCalculator.cosineSimilarity(sourceVector, destination.centroid)
            if (centroidScore < centroidCutoff) {
                return@mapNotNull null
            }

            val topKResult = SimilarityCalculator.topKFromMatrix(
                query = sourceVector,
                matrix = destination.vectors,
                ids = destination.imageIds,
                dim = destination.dim,
                count = destination.count,
                k = topK
            )

            if (topKResult.size == 0) return@mapNotNull null

            var topKSum = 0f
            var topKMax = topKResult.scores[0]
            for (i in 0 until topKResult.size) {
                topKSum += topKResult.scores[i]
                if (topKResult.scores[i] > topKMax) topKMax = topKResult.scores[i]
            }
            val topKMean = topKSum / topKResult.size

            val referenceSupport = SimilarityCalculator.computeReferenceSupport(
                topKResult.scores, topKResult.size
            )
            val combinedScore = SimilarityCalculator.computeScore(
                centroidScore = centroidScore,
                topKMean = topKMean,
                topKMax = topKMax,
                referenceSupport = referenceSupport,
                referenceCount = destination.count
            )
            DestinationCandidate(
                destinationFolderId = destination.folderId,
                score = combinedScore,
                centroidScore = centroidScore,
                topKScore = topKMean,
                topSimilarIds = topKResult.ids,
                topSimilarScores = topKResult.scores,
                topKSize = topKResult.size
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
        val topCandidates = rankedCandidates.take(MAX_STORED_CANDIDATES)

        return SourceSuggestion(
            imageId = imageId,
            assignedDestinationId = if (best.score >= threshold) best.destinationFolderId else 0L,
            best = best,
            secondBestScore = secondBestScore,
            candidateIds = topCandidates.map { it.destinationFolderId },
            candidateScores = topCandidates.map { it.score }
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
