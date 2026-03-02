package com.smartfolder.domain.model

enum class IndexingPhase {
    IDLE,
    LISTING_FILES,
    EMBEDDING,
    COMPLETE,
    ERROR
}

data class IndexingProgress(
    val phase: IndexingPhase = IndexingPhase.IDLE,
    val current: Int = 0,
    val total: Int = 0,
    val currentFileName: String = "",
    val errorMessage: String? = null
)
