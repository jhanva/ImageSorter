package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.ml.SimilarityCalculator
import kotlin.math.max
import kotlin.math.min

internal object ManualVisualClusterer {
    private const val VISUAL_SEED_THRESHOLD = 0.918f
    private const val MIN_CLUSTER_MAX_SIMILARITY = 0.93f
    private const val MIN_CLUSTER_AVERAGE_SIMILARITY = 0.91f
    private const val MIN_CLUSTER_ANCHOR_SIMILARITY = 0.90f
    private const val ADAPTIVE_CLUSTERING_MIN_CANDIDATES = 12
    private const val DUPLICATE_CLUSTER_THRESHOLD = 0.992f
    private const val MIN_DUPLICATE_SIZE_RATIO = 0.9f

    data class ClusterResult(
        val duplicateGroupKeys: Map<Long, String> = emptyMap(),
        val visualGroupKeys: Map<Long, String> = emptyMap()
    )

    private data class ClusterThresholds(
        val seed: Float,
        val max: Float,
        val average: Float,
        val anchor: Float
    )

    fun clusterSuggestions(
        suggestions: List<SuggestionItem>,
        embeddingsByImageId: Map<Long, Embedding>
    ): ClusterResult {
        val candidates = suggestions.filter { it.image.id in embeddingsByImageId }
        if (candidates.size < 2) return ClusterResult()
        val similarityMatrix = buildSimilarityMatrix(candidates, embeddingsByImageId)
        val thresholds = resolveThresholds(candidates.size, similarityMatrix)

        val duplicateUnionFind = UnionFind(candidates.size)

        candidates
            .withIndex()
            .groupBy { it.value.image.contentHash }
            .values
            .filter { indexed -> indexed.size > 1 }
            .forEach { indexed ->
                val firstIndex = indexed.first().index
                indexed.drop(1).forEach { duplicateUnionFind.union(firstIndex, it.index) }
            }

        for (i in 0 until candidates.lastIndex) {
            val left = candidates[i]
            for (j in i + 1 until candidates.size) {
                val right = candidates[j]
                val similarity = similarityMatrix[i][j]
                if (isNearDuplicate(left, right, similarity)) {
                    duplicateUnionFind.union(i, j)
                }
            }
        }

        val visualGroupKeys = buildVisualGroupKeys(
            candidates = candidates,
            embeddingsByImageId = embeddingsByImageId,
            similarityMatrix = similarityMatrix,
            thresholds = thresholds
        )

        return ClusterResult(
            duplicateGroupKeys = buildGroupKeys(candidates, duplicateUnionFind, "duplicate"),
            visualGroupKeys = visualGroupKeys
        )
    }

    private fun buildVisualGroupKeys(
        candidates: List<SuggestionItem>,
        embeddingsByImageId: Map<Long, Embedding>,
        similarityMatrix: Array<FloatArray>,
        thresholds: ClusterThresholds
    ): Map<Long, String> {
        val sortedIndices = candidates.indices.sortedWith(
            compareByDescending<Int> { candidates[it].image.sizeBytes }
                .thenByDescending { candidates[it].image.lastModified }
        )
        val clusters = mutableListOf<MutableList<Int>>()

        sortedIndices.forEach { candidateIndex ->
            val candidate = candidates[candidateIndex]
            if (embeddingsByImageId[candidate.image.id] == null) return@forEach

            val bestClusterIndex = clusters
                .mapIndexedNotNull { clusterIndex, cluster ->
                    val match = scoreClusterMatch(
                        candidateIndex = candidateIndex,
                        cluster = cluster,
                        candidates = candidates,
                        embeddingsByImageId = embeddingsByImageId,
                        similarityMatrix = similarityMatrix,
                        thresholds = thresholds
                    )
                    if (match.shouldJoin) {
                        clusterIndex to match.score
                    } else {
                        null
                    }
                }
                .maxByOrNull { it.second }
                ?.first

            if (bestClusterIndex == null) {
                clusters += mutableListOf(candidateIndex)
            } else {
                clusters[bestClusterIndex] += candidateIndex
            }
        }

        return buildMap {
            clusters
                .filter { it.size > 1 }
                .forEachIndexed { clusterIndex, cluster ->
                    val clusterKey = "visual-$clusterIndex"
                    cluster.forEach { index ->
                        put(candidates[index].image.id, clusterKey)
                    }
                }
        }
    }

    private data class ClusterMatch(
        val shouldJoin: Boolean,
        val score: Float
    )

    private fun scoreClusterMatch(
        candidateIndex: Int,
        cluster: List<Int>,
        candidates: List<SuggestionItem>,
        embeddingsByImageId: Map<Long, Embedding>,
        similarityMatrix: Array<FloatArray>,
        thresholds: ClusterThresholds
    ): ClusterMatch {
        val similarities = cluster
            .map { memberIndex -> similarityMatrix[candidateIndex][memberIndex] }
            .sortedDescending()

        if (similarities.isEmpty()) return ClusterMatch(shouldJoin = false, score = 0f)

        val anchor = candidates[cluster.first()]
        if (embeddingsByImageId[anchor.image.id] == null) return ClusterMatch(false, 0f)
        val anchorSimilarity = similarityMatrix[candidateIndex][cluster.first()]
        val maxSimilarity = similarities.first()
        val averageSimilarity = similarities
            .take(min(3, similarities.size))
            .average()
            .toFloat()
        val candidate = candidates[candidateIndex]
        val sizeRatio = resolveSizeRatio(candidate, anchor)
        val score = computeClusterScore(
            maxSimilarity = maxSimilarity,
            averageSimilarity = averageSimilarity,
            anchorSimilarity = anchorSimilarity,
            sizeRatio = sizeRatio
        )

        if (cluster.size == 1) {
            return ClusterMatch(
                shouldJoin = maxSimilarity >= thresholds.seed,
                score = score
            )
        }

        val shouldJoin = maxSimilarity >= thresholds.max &&
            averageSimilarity >= thresholds.average &&
            anchorSimilarity >= thresholds.anchor

        return ClusterMatch(
            shouldJoin = shouldJoin,
            score = score
        )
    }

    private fun isNearDuplicate(
        left: SuggestionItem,
        right: SuggestionItem,
        similarity: Float
    ): Boolean {
        if (similarity < DUPLICATE_CLUSTER_THRESHOLD) return false
        val smaller = min(left.image.sizeBytes, right.image.sizeBytes).toFloat()
        val larger = max(left.image.sizeBytes, right.image.sizeBytes).toFloat()
        if (smaller <= 0f || larger <= 0f) return false
        return (smaller / larger) >= MIN_DUPLICATE_SIZE_RATIO
    }

    private fun computeClusterScore(
        maxSimilarity: Float,
        averageSimilarity: Float,
        anchorSimilarity: Float,
        sizeRatio: Float
    ): Float {
        val sizeBonus = ((sizeRatio - 0.70f).coerceAtLeast(0f) / 0.30f) * 0.01f
        return (maxSimilarity * 0.45f) +
            (averageSimilarity * 0.40f) +
            (anchorSimilarity * 0.15f) +
            sizeBonus
    }

    private fun resolveSizeRatio(left: SuggestionItem, right: SuggestionItem): Float {
        val smaller = min(left.image.sizeBytes, right.image.sizeBytes).toFloat()
        val larger = max(left.image.sizeBytes, right.image.sizeBytes).toFloat()
        if (smaller <= 0f || larger <= 0f) return 0f
        return smaller / larger
    }

    private fun buildSimilarityMatrix(
        candidates: List<SuggestionItem>,
        embeddingsByImageId: Map<Long, Embedding>
    ): Array<FloatArray> {
        val matrix = Array(candidates.size) { FloatArray(candidates.size) }
        for (i in candidates.indices) {
            matrix[i][i] = 1f
        }
        for (i in 0 until candidates.lastIndex) {
            val leftEmbedding = embeddingsByImageId[candidates[i].image.id] ?: continue
            for (j in i + 1 until candidates.size) {
                val rightEmbedding = embeddingsByImageId[candidates[j].image.id] ?: continue
                val similarity = SimilarityCalculator.cosineSimilarity(
                    leftEmbedding.vector,
                    rightEmbedding.vector
                )
                matrix[i][j] = similarity
                matrix[j][i] = similarity
            }
        }
        return matrix
    }

    private fun resolveThresholds(
        candidateCount: Int,
        similarityMatrix: Array<FloatArray>
    ): ClusterThresholds {
        if (candidateCount < ADAPTIVE_CLUSTERING_MIN_CANDIDATES) {
            return defaultThresholds()
        }

        val similarities = mutableListOf<Float>()
        for (i in 0 until similarityMatrix.lastIndex) {
            for (j in i + 1 until similarityMatrix.size) {
                similarities += similarityMatrix[i][j]
            }
        }
        if (similarities.size < 20) {
            return defaultThresholds()
        }

        similarities.sort()
        val adaptiveSeed = percentile(similarities, 0.90f)
            .coerceIn(VISUAL_SEED_THRESHOLD, 0.965f)
        val adaptiveMax = max(MIN_CLUSTER_MAX_SIMILARITY, adaptiveSeed)
        val adaptiveAverage = max(
            MIN_CLUSTER_AVERAGE_SIMILARITY,
            (adaptiveSeed - 0.01f).coerceAtMost(adaptiveMax)
        )
        val adaptiveAnchor = max(
            MIN_CLUSTER_ANCHOR_SIMILARITY,
            adaptiveSeed - 0.025f
        )
        return ClusterThresholds(
            seed = adaptiveSeed,
            max = adaptiveMax,
            average = adaptiveAverage,
            anchor = adaptiveAnchor
        )
    }

    private fun percentile(values: List<Float>, ratio: Float): Float {
        if (values.isEmpty()) return 0f
        val index = ((values.lastIndex) * ratio).toInt().coerceIn(0, values.lastIndex)
        return values[index]
    }

    private fun defaultThresholds() = ClusterThresholds(
        seed = VISUAL_SEED_THRESHOLD,
        max = MIN_CLUSTER_MAX_SIMILARITY,
        average = MIN_CLUSTER_AVERAGE_SIMILARITY,
        anchor = MIN_CLUSTER_ANCHOR_SIMILARITY
    )

    private fun buildGroupKeys(
        candidates: List<SuggestionItem>,
        unionFind: UnionFind,
        prefix: String
    ): Map<Long, String> {
        val groupedIndices = candidates.indices.groupBy { unionFind.find(it) }
        return buildMap {
            groupedIndices
                .filterValues { it.size > 1 }
                .forEach { (root, indices) ->
                    val clusterKey = "$prefix-$root"
                    indices.forEach { index ->
                        put(candidates[index].image.id, clusterKey)
                    }
                }
        }
    }

    private class UnionFind(size: Int) {
        private val parent = IntArray(size) { it }

        fun find(index: Int): Int {
            if (parent[index] != index) {
                parent[index] = find(parent[index])
            }
            return parent[index]
        }

        fun union(left: Int, right: Int) {
            val leftRoot = find(left)
            val rightRoot = find(right)
            if (leftRoot != rightRoot) {
                parent[rightRoot] = leftRoot
            }
        }
    }
}
