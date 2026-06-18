package com.smartfolder.ml

object SimilarityCalculator {
    private const val CENTROID_WEIGHT = 0.20f
    private const val TOP_K_MEAN_WEIGHT = 0.25f
    private const val TOP_K_MAX_WEIGHT = 0.20f
    private const val REFERENCE_SUPPORT_WEIGHT = 0.35f

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }
        var dot = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot
    }

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

    fun topKFromMatrix(
        query: FloatArray,
        matrix: FloatArray,
        ids: LongArray,
        dim: Int,
        count: Int,
        k: Int
    ): TopKResult {
        val heapIds = LongArray(k)
        val heapScores = FloatArray(k) { Float.NEGATIVE_INFINITY }
        var heapSize = 0
        var heapMinIdx = 0

        var offset = 0
        for (row in 0 until count) {
            var dot = 0f
            for (d in 0 until dim) {
                dot += query[d] * matrix[offset + d]
            }
            offset += dim

            if (heapSize < k) {
                heapIds[heapSize] = ids[row]
                heapScores[heapSize] = dot
                heapSize++
                if (heapSize == k) {
                    heapMinIdx = minIndex(heapScores, heapSize)
                }
            } else if (dot > heapScores[heapMinIdx]) {
                heapIds[heapMinIdx] = ids[row]
                heapScores[heapMinIdx] = dot
                heapMinIdx = minIndex(heapScores, heapSize)
            }
        }

        sortDescending(heapIds, heapScores, heapSize)
        return TopKResult(heapIds, heapScores, heapSize)
    }

    private fun minIndex(scores: FloatArray, size: Int): Int {
        var idx = 0
        var min = scores[0]
        for (i in 1 until size) {
            if (scores[i] < min) {
                min = scores[i]
                idx = i
            }
        }
        return idx
    }

    private fun sortDescending(ids: LongArray, scores: FloatArray, size: Int) {
        for (i in 0 until size - 1) {
            var maxIdx = i
            for (j in i + 1 until size) {
                if (scores[j] > scores[maxIdx]) maxIdx = j
            }
            if (maxIdx != i) {
                val tmpS = scores[i]; scores[i] = scores[maxIdx]; scores[maxIdx] = tmpS
                val tmpI = ids[i]; ids[i] = ids[maxIdx]; ids[maxIdx] = tmpI
            }
        }
    }

    class TopKResult(val ids: LongArray, val scores: FloatArray, val size: Int)

    fun computeReferenceSupport(scores: FloatArray, size: Int): Float {
        if (size == 0) return 0f
        if (size == 1) return scores[0]
        var sum = 0f
        for (i in 1 until size) sum += scores[i]
        return sum / (size - 1)
    }

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
