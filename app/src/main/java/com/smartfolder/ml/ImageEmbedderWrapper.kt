package com.smartfolder.ml

import android.content.Context
import android.graphics.Bitmap
import com.smartfolder.domain.model.ModelBackend
import com.smartfolder.domain.model.ModelChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a small pool of embedder sessions (one per concurrent worker) instead of a single
 * shared instance, so concurrent embed() calls run inference in parallel rather than queueing
 * behind one session. Each session in the pool is only ever used by one caller at a time.
 */
@Singleton
class ImageEmbedderWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()

    @Volatile private var currentModelFile: String? = null
    @Volatile private var currentBackend: ModelBackend? = null
    @Volatile private var currentPoolSize: Int = 0
    private val mediaPipeSessions = mutableListOf<MediaPipeImageEmbedderSession>()
    private val clipSessions = mutableListOf<MobileClipSession>()
    @Volatile private var mediaPipePool: Channel<MediaPipeImageEmbedderSession>? = null
    @Volatile private var clipPool: Channel<MobileClipSession>? = null

    suspend fun initialize(modelFileName: String, poolSize: Int = 1) = mutex.withLock {
        val backend = backendForFile(modelFileName)
        val size = poolSize.coerceAtLeast(1)
        if (currentModelFile == modelFileName && currentBackend == backend && currentPoolSize == size) {
            return@withLock
        }

        closeInternal()

        when (backend) {
            ModelBackend.MEDIAPIPE_TFLITE -> {
                repeat(size) {
                    mediaPipeSessions.add(MediaPipeImageEmbedderSession.create(context, modelFileName))
                }
                val pool = Channel<MediaPipeImageEmbedderSession>(size)
                mediaPipeSessions.forEach { pool.trySend(it) }
                mediaPipePool = pool
            }
            ModelBackend.ONNX_CLIP -> {
                val fallbackInputSize = ModelChoice.entries
                    .firstOrNull { it.modelFileName == modelFileName }
                    ?.onnxInputFallback
                    ?: 256
                repeat(size) {
                    clipSessions.add(MobileClipSession.create(context, modelFileName, fallbackInputSize, size))
                }
                val pool = Channel<MobileClipSession>(size)
                clipSessions.forEach { pool.trySend(it) }
                clipPool = pool
            }
        }
        currentModelFile = modelFileName
        currentBackend = backend
        currentPoolSize = size
    }

    suspend fun embed(bitmap: Bitmap): FloatArray? {
        return when (currentBackend) {
            ModelBackend.MEDIAPIPE_TFLITE -> {
                val pool = mediaPipePool ?: return null
                val session = pool.receive()
                try {
                    session.embed(bitmap)
                } finally {
                    pool.send(session)
                }
            }
            ModelBackend.ONNX_CLIP -> {
                val pool = clipPool ?: return null
                val session = pool.receive()
                try {
                    session.embed(bitmap)
                } finally {
                    pool.send(session)
                }
            }
            null -> null
        }
    }

    suspend fun close() = mutex.withLock {
        closeInternal()
        currentModelFile = null
        currentBackend = null
        currentPoolSize = 0
    }

    private fun closeInternal() {
        mediaPipePool?.close()
        mediaPipePool = null
        mediaPipeSessions.forEach { it.close() }
        mediaPipeSessions.clear()

        clipPool?.close()
        clipPool = null
        clipSessions.forEach { it.close() }
        clipSessions.clear()
    }

    private fun backendForFile(modelFileName: String): ModelBackend {
        val matched = ModelChoice.entries.firstOrNull { it.modelFileName == modelFileName }
        if (matched != null) return matched.backend
        return if (modelFileName.endsWith(".onnx", ignoreCase = true)) {
            ModelBackend.ONNX_CLIP
        } else {
            ModelBackend.MEDIAPIPE_TFLITE
        }
    }
}
