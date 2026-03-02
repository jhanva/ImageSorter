package com.smartfolder.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SimilarityCalculatorTest {

    @Test
    fun `identical vectors have similarity 1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        assertEquals(1f, SimilarityCalculator.cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `orthogonal vectors have similarity 0`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        assertEquals(0f, SimilarityCalculator.cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `opposite vectors have similarity -1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        assertEquals(-1f, SimilarityCalculator.cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `similar vectors have high similarity`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1f, 2f, 3.1f)
        val similarity = SimilarityCalculator.cosineSimilarity(a, b)
        assert(similarity > 0.99f) { "Expected high similarity, got $similarity" }
    }

    @Test
    fun `zero vector returns 0`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
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

    @Test
    fun `computeScore uses correct weights`() {
        val centroidScore = 0.8f
        val topKScore = 0.9f
        val expected = 0.4f * centroidScore + 0.6f * topKScore
        assertEquals(expected, SimilarityCalculator.computeScore(centroidScore, topKScore), 0.0001f)
    }

    @Test
    fun `computeScore with zero scores returns 0`() {
        assertEquals(0f, SimilarityCalculator.computeScore(0f, 0f), 0.0001f)
    }

    @Test
    fun `computeScore with perfect scores returns 1`() {
        assertEquals(1f, SimilarityCalculator.computeScore(1f, 1f), 0.0001f)
    }
}
