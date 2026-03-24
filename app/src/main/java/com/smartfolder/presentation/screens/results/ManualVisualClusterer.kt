package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.ml.SimilarityCalculator
import kotlin.math.max
import kotlin.math.min

internal object ManualVisualClusterer {
    private const val VISUAL_CLUSTER_THRESHOLD = 0.94f
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
        val visualUnionFind = UnionFind(candidates.size)

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
                    visualUnionFind.union(i, j)
                }
                if (isNearDuplicate(left, right, similarity)) {
                    duplicateUnionFind.union(i, j)
                }
            }
        }

        return ClusterResult(
            duplicateGroupKeys = buildGroupKeys(candidates, duplicateUnionFind, "duplicate"),
            visualGroupKeys = buildGroupKeys(candidates, visualUnionFind, "visual")
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
