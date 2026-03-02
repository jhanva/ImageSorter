package com.smartfolder.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.sqrt

class CentroidCalculatorTest {

    @Test
    fun `single embedding returns normalized copy`() {
        val embedding = floatArrayOf(3f, 4f)
        val centroid = CentroidCalculator.compute(listOf(embedding))
        val norm = sqrt(3f * 3f + 4f * 4f)
        assertArrayEquals(floatArrayOf(3f / norm, 4f / norm), centroid, 0.0001f)
    }

    @Test
    fun `two identical embeddings return same normalized vector`() {
        val embedding = floatArrayOf(1f, 0f, 0f)
        val centroid = CentroidCalculator.compute(listOf(embedding, embedding))
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), centroid, 0.0001f)
    }

    @Test
    fun `centroid of symmetric vectors`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        val centroid = CentroidCalculator.compute(listOf(a, b))
        // Mean is (0.5, 0.5), normalized is (1/sqrt(2), 1/sqrt(2))
        val expected = 1f / sqrt(2f)
        assertArrayEquals(floatArrayOf(expected, expected), centroid, 0.0001f)
    }

    @Test
    fun `centroid of three vectors`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val c = floatArrayOf(0f, 0f, 1f)
        val centroid = CentroidCalculator.compute(listOf(a, b, c))
        // Mean is (1/3, 1/3, 1/3), normalized
        val norm = sqrt(3f * (1f / 3f) * (1f / 3f))
        val expected = (1f / 3f) / norm
        assertEquals(expected, centroid[0], 0.0001f)
        assertEquals(expected, centroid[1], 0.0001f)
        assertEquals(expected, centroid[2], 0.0001f)
    }

    @Test
    fun `empty list throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            CentroidCalculator.compute(emptyList())
        }
    }

    @Test
    fun `centroid is normalized`() {
        val embeddings = listOf(
            floatArrayOf(3f, 4f, 0f),
            floatArrayOf(1f, 2f, 3f)
        )
        val centroid = CentroidCalculator.compute(embeddings)
        var sumSquares = 0f
        for (v in centroid) sumSquares += v * v
        assertEquals(1f, sqrt(sumSquares), 0.0001f)
    }
}
