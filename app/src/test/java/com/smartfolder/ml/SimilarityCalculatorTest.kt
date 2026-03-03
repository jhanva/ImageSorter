package com.smartfolder.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityCalculatorTest {

    // -- cosineSimilarity (dot product for L2-normalized vectors) --

    @Test
    fun `identical unit vectors have similarity 1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        assertEquals(1f, SimilarityCalculator.cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `orthogonal unit vectors have similarity 0`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        assertEquals(0f, SimilarityCalculator.cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `opposite unit vectors have similarity -1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        assertEquals(-1f, SimilarityCalculator.cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `zero vector dot product returns 0`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        assertEquals(0f, SimilarityCalculator.cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `different length vectors throw exception`() {
        val a = floatArrayOf(1f, 2f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertThrows(IllegalArgumentException::class.java) {
            SimilarityCalculator.cosineSimilarity(a, b)
        }
    }

    // -- cosineSimilarityFull (for non-normalized vectors) --

    @Test
    fun `full cosine similarity with non-unit vectors`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1f, 2f, 3.1f)
        val similarity = SimilarityCalculator.cosineSimilarityFull(a, b)
        assert(similarity > 0.99f) { "Expected high similarity, got $similarity" }
    }

    @Test
    fun `full cosine similarity with zero vector returns 0`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertEquals(0f, SimilarityCalculator.cosineSimilarityFull(a, b), 0.0001f)
    }

    // -- computeScore --

    @Test
    fun `computeScore uses correct weights`() {
        val centroidScore = 0.8f
        val topKMean = 0.7f
        val topKMax = 0.9f
        val expected = 0.2f * centroidScore + 0.3f * topKMean + 0.5f * topKMax
        assertEquals(expected, SimilarityCalculator.computeScore(centroidScore, topKMean, topKMax), 0.0001f)
    }

    @Test
    fun `computeScore with zero scores returns 0`() {
        assertEquals(0f, SimilarityCalculator.computeScore(0f, 0f, 0f), 0.0001f)
    }

    @Test
    fun `computeScore with perfect scores returns 1`() {
        assertEquals(1f, SimilarityCalculator.computeScore(1f, 1f, 1f), 0.0001f)
    }

    @Test
    fun `computeScore prioritizes topKMax over centroid`() {
        // High max similarity should produce higher score than high centroid
        val highMax = SimilarityCalculator.computeScore(0.3f, 0.5f, 0.9f)
        val highCentroid = SimilarityCalculator.computeScore(0.9f, 0.5f, 0.3f)
        assertTrue(highMax > highCentroid)
    }
}
