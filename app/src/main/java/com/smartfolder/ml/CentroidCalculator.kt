package com.smartfolder.ml

object CentroidCalculator {
    fun compute(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "Cannot compute centroid of empty list" }
        val dim = embeddings.first().size
        val mean = FloatArray(dim)
        for (embedding in embeddings) {
            for (i in mean.indices) {
                mean[i] += embedding[i]
            }
        }
        val count = embeddings.size.toFloat()
        for (i in mean.indices) {
            mean[i] /= count
        }
        return EmbeddingNormalizer.normalize(mean)
    }
}
