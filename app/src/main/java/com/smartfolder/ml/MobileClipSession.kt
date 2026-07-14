package com.smartfolder.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.FloatBuffer
import kotlin.math.sqrt

internal class MobileClipSession private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val inputName: String,
    private val inputSize: Int
) {
    companion object {
        private const val TAG = "MobileClipSession"
        private const val EMBED_TIMEOUT_MS = 30_000L

        private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

        fun resolveInputSize(shape: LongArray?, fallback: Int): Int {
            val size = shape?.lastOrNull()?.toInt() ?: return fallback
            return if (size > 0) size else fallback
        }

        suspend fun create(
            context: Context,
            modelFileName: String,
            fallbackInputSize: Int = 256,
            poolSize: Int = 1
        ): MobileClipSession =
            withContext(Dispatchers.IO) {
                val assetPath = "models/$modelFileName"
                val available = context.assets.list("models")?.contains(modelFileName) == true
                if (!available) {
                    error("Model file not bundled in assets: $assetPath")
                }
                val env = OrtEnvironment.getEnvironment()
                val cpuCount = Runtime.getRuntime().availableProcessors()
                val opts = OrtSession.SessionOptions().apply {
                    // Split intra-op threads across the pool so concurrent sessions don't
                    // oversubscribe the CPU (poolSize sessions running at once).
                    setIntraOpNumThreads(
                        (cpuCount / poolSize.coerceAtLeast(1)).coerceIn(1, 4)
                    )
                    runCatching { addNnapi() }.onFailure {
                        Log.w(TAG, "NNAPI delegate unavailable: ${it.message}")
                    }
                }
                val modelBytes = context.assets.open(assetPath).use { it.readBytes() }
                val session = env.createSession(modelBytes, opts)
                val inputName = session.inputNames.firstOrNull()
                    ?: error("ONNX model has no inputs: $modelFileName")
                val inputShape = session.inputInfo[inputName]
                    ?.info
                    ?.let { it as? ai.onnxruntime.TensorInfo }
                    ?.shape
                val inputSize = resolveInputSize(inputShape, fallbackInputSize)
                MobileClipSession(env, session, inputName, inputSize)
            }
    }

    suspend fun embed(bitmap: Bitmap): FloatArray? = withTimeoutOrNull(EMBED_TIMEOUT_MS) {
        withContext(Dispatchers.Default) {
            try {
                val buffer = preprocess(bitmap)
                val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
                OnnxTensor.createTensor(env, buffer, shape).use { tensor ->
                    session.run(mapOf(inputName to tensor)).use { result ->
                        val raw = extractEmbedding(result.get(0).value) ?: return@withContext null
                        l2Normalize(raw)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Inference failed: ${e.message}")
                null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractEmbedding(value: Any?): FloatArray? = when (value) {
        is Array<*> -> (value.firstOrNull() as? FloatArray)
        is FloatArray -> value
        else -> null
    }

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val scaled = if (bitmap.width == inputSize && bitmap.height == inputSize) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        }
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        if (scaled !== bitmap) scaled.recycle()

        val buffer = FloatBuffer.allocate(3 * inputSize * inputSize)
        // CHW layout: R plane, then G plane, then B plane
        for (p in pixels) {
            val r = ((p shr 16) and 0xFF) / 255f
            buffer.put((r - MEAN[0]) / STD[0])
        }
        for (p in pixels) {
            val g = ((p shr 8) and 0xFF) / 255f
            buffer.put((g - MEAN[1]) / STD[1])
        }
        for (p in pixels) {
            val b = (p and 0xFF) / 255f
            buffer.put((b - MEAN[2]) / STD[2])
        }
        buffer.rewind()
        return buffer
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSq = 0f
        for (v in vector) sumSq += v * v
        val norm = sqrt(sumSq)
        if (norm == 0f) return vector
        val out = FloatArray(vector.size)
        for (i in vector.indices) out[i] = vector[i] / norm
        return out
    }

    fun close() {
        runCatching { session.close() }
    }
}
