package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.ml.SimilarityCalculator
import kotlin.math.max
import kotlin.math.min

internal object ManualVisualClusterer {
    private const val VISUAL_CLUSTER_THRESHOLD = 0.93f
    private const val MIN_VISUAL_SIMILARITY = 0.89f
    private const val DUPLICATE_CLUSTER_THRESHOLD = 0.992f
    private const val MIN_DUPLICATE_SIZE_RATIO = 0.9f

    data class ClusterResult(
        val duplicateGroupKeys: Map<Long, String> = emptyMap(),
        val visualGroupKeys: Map<Long, String> = emptyMap()
    )

    fun clusterSuggestions(
        suggestions: List<SuggestionItem>,
        embeddingsByImageId: Map<Long, Embedding>
    ): ClusterResult {
        val candidates = suggestions.filter { it.image.id in embeddingsByImageId }
        if (candidates.size < 2) return ClusterResult()

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
            val leftEmbedding = embeddingsByImageId[left.image.id] ?: continue
            for (j in i + 1 until candidates.size) {
                val right = candidates[j]
                val rightEmbedding = embeddingsByImageId[right.image.id] ?: continue
                val similarity = SimilarityCalculator.cosineSimilarity(
                    leftEmbedding.vector,
                    rightEmbedding.vector
                )
                if (similarity >= VISUAL_CLUSTER_THRESHOLD) {
                    if (isNearDuplicate(left, right, similarity)) {
                        duplicateUnionFind.union(i, j)
                    }
                } else if (isNearDuplicate(left, right, similarity)) {
                    duplicateUnionFind.union(i, j)
                }
            }
        }

        val visualGroupKeys = buildVisualGroupKeys(candidates, embeddingsByImageId)

        return ClusterResult(
            duplicateGroupKeys = buildGroupKeys(candidates, duplicateUnionFind, "duplicate"),
            visualGroupKeys = visualGroupKeys
        )
    }

    private fun buildVisualGroupKeys(
        candidates: List<SuggestionItem>,
        embeddingsByImageId: Map<Long, Embedding>
    ): Map<Long, String> {
        val sortedIndices = candidates.indices.sortedWith(
            compareByDescending<Int> { candidates[it].image.sizeBytes }
                .thenByDescending { candidates[it].image.lastModified }
        )
        val clusters = mutableListOf<MutableList<Int>>()

        sortedIndices.forEach { candidateIndex ->
            val candidate = candidates[candidateIndex]
            val candidateEmbedding = embeddingsByImageId[candidate.image.id] ?: return@forEach

            val bestClusterIndex = clusters
                .mapIndexedNotNull { clusterIndex, cluster ->
                    val anchorIndex = cluster.first()
                    val anchor = candidates[anchorIndex]
                    val anchorEmbedding = embeddingsByImageId[anchor.image.id] ?: return@mapIndexedNotNull null
                    val similarity = SimilarityCalculator.cosineSimilarity(
                        candidateEmbedding.vector,
                        anchorEmbedding.vector
                    )
                    val hybridScore = computeHybridVisualScore(candidate, anchor, similarity)
                    if (shouldJoinVisualCluster(candidate, anchor, similarity, hybridScore)) {
                        clusterIndex to hybridScore
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

    private fun shouldJoinVisualCluster(
        left: SuggestionItem,
        right: SuggestionItem,
        similarity: Float,
        hybridScore: Float
    ): Boolean {
        if (similarity < MIN_VISUAL_SIMILARITY) return false
        if (hybridScore < VISUAL_CLUSTER_THRESHOLD) return false

        val nameOverlap = nameOverlapScore(left.image.displayName, right.image.displayName)
        if (nameOverlap <= 0f && similarity < 0.915f) return false

        return true
    }

    private fun computeHybridVisualScore(
        left: SuggestionItem,
        right: SuggestionItem,
        similarity: Float
    ): Float {
        val nameOverlap = nameOverlapScore(left.image.displayName, right.image.displayName)
        val smaller = min(left.image.sizeBytes, right.image.sizeBytes).toFloat()
        val larger = max(left.image.sizeBytes, right.image.sizeBytes).toFloat()
        val sizeRatio = if (smaller <= 0f || larger <= 0f) 0f else smaller / larger
        val sizeBonus = ((sizeRatio - 0.75f).coerceAtLeast(0f) / 0.25f) * 0.015f
        val nameBonus = nameOverlap * 0.025f
        return similarity + sizeBonus + nameBonus
    }

    private fun nameOverlapScore(leftName: String, rightName: String): Float {
        val leftTokens = normalizedTokens(leftName)
        val rightTokens = normalizedTokens(rightName)
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0f
        val intersection = leftTokens.intersect(rightTokens).size.toFloat()
        val union = (leftTokens + rightTokens).size.toFloat()
        return if (union == 0f) 0f else intersection / union
    }

    private fun normalizedTokens(displayName: String): Set<String> {
        val normalized = ManualReviewOrganizer.normalizeNameGroupKey(displayName)
        return normalized.split(' ')
            .filter { it.isNotBlank() }
            .toSet()
    }

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
