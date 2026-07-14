package com.smartfolder.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Reproduction test for the session-pool change: runs concurrent embed() calls
 * through a pool of 2 ONNX sessions the same way IndexFolderUseCase does with
 * the BALANCED profile, and checks that inference neither crashes nor returns
 * corrupted vectors.
 */
@RunWith(AndroidJUnit4::class)
class ImageEmbedderWrapperPoolTest {

    private val modelFileName = "mobileclip_s0_image.onnx"

    private fun testBitmap(seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = android.graphics.Color.rgb(seed * 37 % 256, seed * 91 % 256, seed * 53 % 256)
        canvas.drawRect(0f, 0f, 384f, 384f, paint)
        paint.color = android.graphics.Color.rgb(255 - seed % 256, seed % 256, 128)
        canvas.drawCircle(192f, 192f, (40 + seed % 120).toFloat(), paint)
        return bitmap
    }

    @Test
    fun concurrentEmbedsWithPoolOf2ProduceValidVectors() = runTest(timeout = kotlin.time.Duration.parse("10m")) {
        val wrapper = ImageEmbedderWrapper(ApplicationProvider.getApplicationContext())
        wrapper.initialize(modelFileName, poolSize = 2)

        val batchSize = 4
        val totalImages = 60
        val vectors = mutableListOf<FloatArray?>()

        for (batchStart in 0 until totalImages step batchSize) {
            val batchResults = coroutineScope {
                (batchStart until (batchStart + batchSize).coerceAtMost(totalImages)).map { i ->
                    async {
                        val bitmap = testBitmap(i)
                        try {
                            wrapper.embed(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }.awaitAll()
            }
            vectors += batchResults
        }

        wrapper.close()

        assertEquals(totalImages, vectors.size)
        vectors.forEachIndexed { i, vector ->
            assertNotNull("Embedding $i was null (inference failed)", vector)
            val v = vector!!
            assertTrue("Embedding $i is empty", v.isNotEmpty())
            assertTrue("Embedding $i contains NaN", v.none { it.isNaN() })
            var norm = 0f
            for (x in v) norm += x * x
            assertTrue("Embedding $i is not L2-normalized (norm=$norm)", abs(norm - 1f) < 0.01f)
        }
    }

    @Test
    fun sameImageGivesSameVectorAcrossPoolSessions() = runTest(timeout = kotlin.time.Duration.parse("10m")) {
        val wrapper = ImageEmbedderWrapper(ApplicationProvider.getApplicationContext())
        wrapper.initialize(modelFileName, poolSize = 2)

        // Embed the same image many times concurrently: if pooled sessions or
        // concurrent preprocessing corrupt data, results will diverge.
        val reference = testBitmap(7)
        val results = coroutineScope {
            (0 until 12).map {
                async {
                    val copy = reference.copy(Bitmap.Config.ARGB_8888, false)
                    try {
                        wrapper.embed(copy)
                    } finally {
                        copy.recycle()
                    }
                }
            }.awaitAll()
        }
        reference.recycle()
        wrapper.close()

        val first = results.first()
        assertNotNull(first)
        results.forEachIndexed { i, vector ->
            assertNotNull("Run $i returned null", vector)
            vector!!
            for (d in first!!.indices) {
                assertTrue(
                    "Run $i diverges at dim $d: ${first[d]} vs ${vector[d]}",
                    abs(first[d] - vector[d]) < 1e-3f
                )
            }
        }
    }
}
