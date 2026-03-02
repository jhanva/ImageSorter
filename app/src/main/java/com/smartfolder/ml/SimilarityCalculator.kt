package com.smartfolder.ml

object SimilarityCalculator {

    /**
     * Dot product similarity for L2-normalized vectors.
     * Since MediaPipe embeddings are already L2-normalized (setL2Normalize=true),
     * cosine similarity simplifies to just the dot product, avoiding two
     * unnecessary sqrt and norm calculations per call.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }
        var dot = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot
    }

    /**
     * Full cosine similarity with norm calculation.
     * Use this when vectors may not be L2-normalized.
     */
    fun cosineSimilarityFull(a: FloatArray, b: FloatArray): Float {
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
