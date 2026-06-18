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

    // -- computeReferenceSupport --

    @Test
    fun `computeReferenceSupport uses supporting matches after the best hit`() {
        val support = SimilarityCalculator.computeReferenceSupport(listOf(0.95f, 0.86f, 0.84f))

        assertEquals(0.85f, support, 0.0001f)
    }

    @Test
    fun `computeReferenceSupport falls back to best hit when only one reference exists`() {
        val support = SimilarityCalculator.computeReferenceSupport(listOf(0.91f))

        assertEquals(0.91f, support, 0.0001f)
    }

    // -- computeScore --

    @Test
    fun `computeScore uses consensus aware weights`() {
        val centroidScore = 0.8f
        val topKMean = 0.7f
        val topKMax = 0.9f
        val referenceSupport = 0.6f
        val expected =
            0.2f * centroidScore + 0.25f * topKMean + 0.2f * topKMax + 0.35f * referenceSupport
        assertEquals(
            expected,
            SimilarityCalculator.computeScore(
                centroidScore = centroidScore,
                topKMean = topKMean,
                topKMax = topKMax,
                referenceSupport = referenceSupport
            ),
            0.0001f
        )
    }

    @Test
    fun `computeScore with zero scores returns 0`() {
        assertEquals(
            0f,
            SimilarityCalculator.computeScore(0f, 0f, 0f, 0f),
            0.0001f
        )
    }

    @Test
    fun `computeScore with perfect scores returns 1`() {
        assertEquals(
            1f,
            SimilarityCalculator.computeScore(1f, 1f, 1f, 1f),
            0.0001f
        )
    }

    // -- topKFromMatrix --

    @Test
    fun `topKFromMatrix returns top k similar vectors sorted descending`() {
        val dim = 3
        val ids = longArrayOf(10L, 20L, 30L, 40L)
        val matrix = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0.9f, 0.1f, 0f,
            0.8f, 0.2f, 0f
        )
        val query = floatArrayOf(1f, 0f, 0f)

        val result = SimilarityCalculator.topKFromMatrix(query, matrix, ids, dim, 4, 2)

        assertEquals(2, result.size)
        assertEquals(10L, result.ids[0])
        assertEquals(1f, result.scores[0], 0.0001f)
        assertEquals(30L, result.ids[1])
        assertTrue(result.scores[0] >= result.scores[1])
    }

    @Test
    fun `topKFromMatrix with k larger than count returns all`() {
        val dim = 2
        val ids = longArrayOf(1L, 2L)
        val matrix = floatArrayOf(0.5f, 0.5f, 0.3f, 0.7f)
        val query = floatArrayOf(1f, 0f)

        val result = SimilarityCalculator.topKFromMatrix(query, matrix, ids, dim, 2, 10)

        assertEquals(2, result.size)
    }

    // -- computeReferenceSupport (FloatArray overload) --

    @Test
    fun `computeReferenceSupport array overload matches list overload`() {
        val scores = floatArrayOf(0.95f, 0.86f, 0.84f)
        val fromArray = SimilarityCalculator.computeReferenceSupport(scores, scores.size)
        val fromList = SimilarityCalculator.computeReferenceSupport(scores.toList())
        assertEquals(fromList, fromArray, 0.0001f)
    }

    @Test
    fun `computeScore rewards reference consensus over a single spiky match`() {
        val strongConsensus = SimilarityCalculator.computeScore(
            centroidScore = 0.92f,
            topKMean = 0.87f,
            topKMax = 0.93f,
            referenceSupport = 0.85f
        )
        val oneOffHit = SimilarityCalculator.computeScore(
            centroidScore = 0.92f,
            topKMean = 0.80f,
            topKMax = 0.99f,
            referenceSupport = 0.65f
        )

        assertTrue(strongConsensus > oneOffHit)
    }
}
