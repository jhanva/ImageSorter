package com.smartfolder.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "suggestions",
    indices = [Index("imageId", unique = true)]
)
data class SuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageId: Long,
    val score: Float,
    val centroidScore: Float,
    val topKScore: Float,
    val topSimilarIds: String,
    val topSimilarScores: String,
    val createdAt: Long
)
