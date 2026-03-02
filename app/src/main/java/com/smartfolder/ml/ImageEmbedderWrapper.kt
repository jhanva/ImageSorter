package com.smartfolder.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageEmbedderWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ImageEmbedderWrapper"
        private const val EMBED_TIMEOUT_MS = 30_000L
    }
    @Volatile
    private var embedder: ImageEmbedder? = null
    @Volatile
    private var currentModelName: String? = null
    private val mutex = Mutex()

    suspend fun initialize(modelFileName: String) = mutex.withLock {
        if (currentModelName == modelFileName && embedder != null) return@withLock

        embedder?.close()
        embedder = null
        currentModelName = null

        withContext(Dispatchers.IO) {
            val baseOptions = try {
                BaseOptions.builder()
                    .setModelAssetPath("models/$modelFileName")
                    .setDelegate(Delegate.GPU)
                    .build()
                    .also { Log.i(TAG, "Initialized with GPU delegate") }
            } catch (e: Exception) {
                Log.w(TAG, "GPU delegate unavailable, falling back to CPU: ${e.message}")
                BaseOptions.builder()
                    .setModelAssetPath("models/$modelFileName")
                    .setDelegate(Delegate.CPU)
                    .build()
            }

            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .setL2Normalize(true)
                .setQuantize(false)
                .build()

            val instance = ImageEmbedder.createFromOptions(context, options)
            embedder = instance
            currentModelName = modelFileName
        }
    }

    suspend fun embed(bitmap: Bitmap): FloatArray? {
        // Only hold mutex briefly to get embedder reference
        val embedderInstance = mutex.withLock {
            embedder
        } ?: return null

        // Inference runs outside the lock on IO dispatcher
        return withTimeoutOrNull(EMBED_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                try {
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    try {
                        val result = embedderInstance.embed(mpImage)
                        val embedding = result.embeddingResult().embeddings().firstOrNull()
                        embedding?.floatEmbedding()?.let { list ->
                            FloatArray(list.size) { list[it] }
                        }
                    } finally {
                        mpImage.close()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun close() = mutex.withLock {
        embedder?.close()
        embedder = null
        currentModelName = null
    }

    fun getCurrentModelName(): String? = currentModelName
}
