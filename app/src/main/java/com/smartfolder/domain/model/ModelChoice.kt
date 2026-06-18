package com.smartfolder.domain.model

enum class ModelBackend { MEDIAPIPE_TFLITE, ONNX_CLIP }

enum class ModelChoice(
    val modelFileName: String,
    val displayName: String,
    val backend: ModelBackend
) {
    FAST("mobilenet_v3_small.tflite", "Fast", ModelBackend.MEDIAPIPE_TFLITE),
    PRECISE("mobilenet_v3_large.tflite", "Precise", ModelBackend.MEDIAPIPE_TFLITE),
    SEMANTIC("mobileclip_s0_image.onnx", "Semantic", ModelBackend.ONNX_CLIP)
}
