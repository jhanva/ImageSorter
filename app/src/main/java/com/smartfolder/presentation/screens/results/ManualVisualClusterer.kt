package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.Embedding
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.ml.SimilarityCalculator

internal object ManualVisualClusterer {
    private const val VISUAL_CLUSTER_THRESHOLD = 0.94f

    fun clusterSuggestions(
        suggestions: List<SuggestionItem>,
        embeddingsByImageId: Map<Long, Embedding>
    ): Map<Long, String> {
        val candidates = suggestions.filter { it.image.id in embeddingsByImageId }
        if (candidates.size < 2) return emptyMap()

        val unionFind = UnionFind(candidates.size)
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
                    unionFind.union(i, j)
                }
            }
        }

        val groupedIndices = candidates.indices.groupBy { unionFind.find(it) }
        return buildMap {
            groupedIndices
                .filterValues { it.size > 1 }
                .forEach { (root, indices) ->
                    val clusterKey = "visual-$root"
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
