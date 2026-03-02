package com.smartfolder.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class EmbeddingNormalizerTest {

    @Test
    fun `normalize unit vector returns same vector`() {
        val input = floatArrayOf(1f, 0f, 0f)
        val result = EmbeddingNormalizer.normalize(input)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), result, 0.0001f)
    }

    @Test
    fun `normalize scales to unit length`() {
        val input = floatArrayOf(3f, 4f)
        val result = EmbeddingNormalizer.normalize(input)
        assertArrayEquals(floatArrayOf(0.6f, 0.8f), result, 0.0001f)
    }

    @Test
    fun `normalized vector has unit norm`() {
        val input = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        val result = EmbeddingNormalizer.normalize(input)
        var sumSquares = 0f
        for (v in result) sumSquares += v * v
        assertEquals(1f, sqrt(sumSquares), 0.0001f)
    }

    @Test
    fun `zero vector returns zero vector`() {
        val input = floatArrayOf(0f, 0f, 0f)
        val result = EmbeddingNormalizer.normalize(input)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), result, 0.0001f)
    }

    @Test
    fun `normalize does not modify original`() {
        val input = floatArrayOf(3f, 4f)
        EmbeddingNormalizer.normalize(input)
        assertArrayEquals(floatArrayOf(3f, 4f), input, 0.0001f)
    }

    @Test
    fun `negative values are handled correctly`() {
        val input = floatArrayOf(-3f, 4f)
        val result = EmbeddingNormalizer.normalize(input)
        assertEquals(-0.6f, result[0], 0.0001f)
        assertEquals(0.8f, result[1], 0.0001f)
    }

    @Test
    fun `single element vector`() {
        val input = floatArrayOf(5f)
        val result = EmbeddingNormalizer.normalize(input)
        assertArrayEquals(floatArrayOf(1f), result, 0.0001f)
    }
}
