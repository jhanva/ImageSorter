package com.smartfolder.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class MediaPipeImageEmbedderSession private constructor(
    private val context: Context,
    private val modelFileName: String,
    @Volatile private var embedder: ImageEmbedder,
    @Volatile private var delegate: Delegate
) {
    companion object {
        private const val TAG = "MediaPipeSession"
        private const val EMBED_TIMEOUT_MS = 30_000L

        suspend fun create(context: Context, modelFileName: String): MediaPipeImageEmbedderSession =
            withContext(Dispatchers.IO) {
                val (embedder, delegate) = buildEmbedder(context, modelFileName, forceCpu = false)
                MediaPipeImageEmbedderSession(context, modelFileName, embedder, delegate)
            }

        private fun buildEmbedder(
            context: Context,
            modelFileName: String,
            forceCpu: Boolean
        ): Pair<ImageEmbedder, Delegate> {
            val (baseOptions, resolvedDelegate) = if (forceCpu) {
                BaseOptions.builder()
                    .setModelAssetPath("models/$modelFileName")
                    .setDelegate(Delegate.CPU)
                    .build() to Delegate.CPU
            } else {
                try {
                    BaseOptions.builder()
                        .setModelAssetPath("models/$modelFileName")
                        .setDelegate(Delegate.GPU)
                        .build()
                        .also { Log.i(TAG, "Initialized with GPU delegate") } to Delegate.GPU
                } catch (e: Exception) {
                    Log.w(TAG, "GPU delegate unavailable, falling back to CPU: ${e.message}")
                    BaseOptions.builder()
                        .setModelAssetPath("models/$modelFileName")
                        .setDelegate(Delegate.CPU)
                        .build() to Delegate.CPU
                }
            }
            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .setL2Normalize(true)
                .setQuantize(false)
                .build()
            return ImageEmbedder.createFromOptions(context, options) to resolvedDelegate
        }
    }

    suspend fun embed(bitmap: Bitmap): FloatArray? {
        val initial = runEmbed(bitmap)
        if (initial != null) return initial

        if (delegate == Delegate.GPU) {
            // Hot-swap to CPU and retry once
            withContext(Dispatchers.IO) {
                val (newEmbedder, newDelegate) = buildEmbedder(context, modelFileName, forceCpu = true)
                embedder.close()
                embedder = newEmbedder
                delegate = newDelegate
            }
            return runEmbed(bitmap)
        }
        return null
    }

    private suspend fun runEmbed(bitmap: Bitmap): FloatArray? =
        withTimeoutOrNull(EMBED_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                try {
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    try {
                        val result = embedder.embed(mpImage)
                        val embedding = result.embeddingResult().embeddings().firstOrNull()
                        embedding?.floatEmbedding()?.let { list ->
                            FloatArray(list.size) { list[it] }
                        }
                    } finally {
                        mpImage.close()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }

    fun close() {
        runCatching { embedder.close() }
    }
}
