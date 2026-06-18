package com.smartfolder.ml

import android.content.Context
import android.graphics.Bitmap
import com.smartfolder.domain.model.ModelBackend
import com.smartfolder.domain.model.ModelChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageEmbedderWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()

    @Volatile private var currentModelFile: String? = null
    @Volatile private var currentBackend: ModelBackend? = null
    @Volatile private var mediaPipeSession: MediaPipeImageEmbedderSession? = null
    @Volatile private var clipSession: MobileClipSession? = null

    suspend fun initialize(modelFileName: String) = mutex.withLock {
        val backend = backendForFile(modelFileName)
        if (currentModelFile == modelFileName && currentBackend == backend) return@withLock

        closeInternal()

        when (backend) {
            ModelBackend.MEDIAPIPE_TFLITE -> {
                mediaPipeSession = MediaPipeImageEmbedderSession.create(context, modelFileName)
            }
            ModelBackend.ONNX_CLIP -> {
                clipSession = MobileClipSession.create(context, modelFileName)
            }
        }
        currentModelFile = modelFileName
        currentBackend = backend
    }

    suspend fun embed(bitmap: Bitmap): FloatArray? {
        val snapshot = mutex.withLock {
            Triple(mediaPipeSession, clipSession, currentBackend)
        }
        return when (snapshot.third) {
            ModelBackend.MEDIAPIPE_TFLITE -> snapshot.first?.embed(bitmap)
            ModelBackend.ONNX_CLIP -> snapshot.second?.embed(bitmap)
            null -> null
        }
    }

    suspend fun close() = mutex.withLock {
        closeInternal()
        currentModelFile = null
        currentBackend = null
    }

    private fun closeInternal() {
        mediaPipeSession?.close()
        mediaPipeSession = null
        clipSession?.close()
        clipSession = null
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
