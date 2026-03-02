package com.smartfolder.ml

object SimilarityCalculator {
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator == 0f) 0f else dot / denominator
    }

    fun computeScore(centroidScore: Float, topKScore: Float): Float {
        return 0.4f * centroidScore + 0.6f * topKScore
    }
}
