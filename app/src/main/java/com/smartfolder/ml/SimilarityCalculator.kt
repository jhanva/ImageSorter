package com.smartfolder.ml

object SimilarityCalculator {
    private const val CENTROID_WEIGHT = 0.20f
    private const val TOP_K_MEAN_WEIGHT = 0.25f
    private const val TOP_K_MAX_WEIGHT = 0.20f
    private const val REFERENCE_SUPPORT_WEIGHT = 0.35f

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

    /**
     * Measures how much the best match is backed up by other strong references.
     * For character and series folders, this helps reject one-off stylistic matches.
     */
    fun computeReferenceSupport(sortedTopScores: List<Float>): Float {
        if (sortedTopScores.isEmpty()) return 0f
        if (sortedTopScores.size == 1) return sortedTopScores.first()
        return sortedTopScores.drop(1).average().toFloat()
    }

    fun computeScore(
        centroidScore: Float,
        topKMean: Float,
        topKMax: Float,
        referenceSupport: Float
    ): Float {
        return (CENTROID_WEIGHT * centroidScore) +
            (TOP_K_MEAN_WEIGHT * topKMean) +
            (TOP_K_MAX_WEIGHT * topKMax) +
            (REFERENCE_SUPPORT_WEIGHT * referenceSupport)
    }
}
