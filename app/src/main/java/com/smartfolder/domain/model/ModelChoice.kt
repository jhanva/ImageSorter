package com.smartfolder.domain.model

enum class ModelChoice(val modelFileName: String, val displayName: String) {
    FAST("mobilenet_v3_small.tflite", "Fast"),
    PRECISE("mobilenet_v3_large.tflite", "Precise")
}
