package com.smartfolder.domain.model

enum class AnalysisPhase {
    IDLE,
    INDEXING_REF,
    INDEXING_UNSORTED,
    CENTROID,
    COMPARING,
    COMPLETE,
    ERROR
}

data class AnalysisProgress(
    val phase: AnalysisPhase = AnalysisPhase.IDLE,
    val current: Int = 0,
    val total: Int = 0,
    val currentFileName: String = "",
    val errorMessage: String? = null
)
