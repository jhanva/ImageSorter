package com.smartfolder.ml

import android.content.Context
import android.graphics.Bitmap
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
            } catch (e: Exception) {
                // Fallback to CPU if GPU is not available
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

    companion object {
        private const val EMBED_TIMEOUT_MS = 30_000L
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
                    val result = embedderInstance.embed(mpImage)
                    val embedding = result.embeddingResult().embeddings().firstOrNull()
                    embedding?.floatEmbedding()?.let { list ->
                        FloatArray(list.size) { list[it] }
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
