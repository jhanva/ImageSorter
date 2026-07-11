package com.smartfolder.domain.model

import android.content.Context

enum class ModelBackend { MEDIAPIPE_TFLITE, ONNX_CLIP }

enum class ModelChoice(
    val modelFileName: String,
    val displayName: String,
    val backend: ModelBackend,
    // Spatial input size used when the ONNX model declares dynamic dimensions.
    val onnxInputFallback: Int = 256
) {
    FAST("mobilenet_v3_small.tflite", "Fast", ModelBackend.MEDIAPIPE_TFLITE),
    PRECISE("mobilenet_v3_large.tflite", "Precise", ModelBackend.MEDIAPIPE_TFLITE),
    SEMANTIC("mobileclip_s0_image.onnx", "Semantic", ModelBackend.ONNX_CLIP),
    // CCIP: contrastive model trained on anime character identity
    // (deepghs/ccip_onnx). Same CLIP-style preprocessing, 384 px input.
    ANIME("ccip_caformer_24_feat.onnx", "Anime", ModelBackend.ONNX_CLIP, onnxInputFallback = 384);

    companion object {
        // MobileCLIP captures semantic content (characters, art style, franchise),
        // which is what folder routing needs for anime and game art libraries.
        val DEFAULT: ModelChoice = SEMANTIC

        fun availableIn(context: Context): List<ModelChoice> {
            val assetFiles = context.assets.list("models")?.toSet() ?: emptySet()
            return entries.filter { it.modelFileName in assetFiles }
        }
    }
}
