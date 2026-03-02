package com.smartfolder.domain.model

data class Decision(
    val id: Long = 0,
    val imageId: Long,
    val accepted: Boolean,
    val score: Float,
    val decidedAt: Long = System.currentTimeMillis()
)
