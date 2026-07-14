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
    val destinationFolderId: Long,
    val score: Float,
    val secondBestScore: Float,
    val centroidScore: Float,
    val topKScore: Float,
    val topSimilarIds: String,
    val topSimilarScores: String,
    @androidx.room.ColumnInfo(defaultValue = "") val candidateIds: String = "",
    @androidx.room.ColumnInfo(defaultValue = "") val candidateScores: String = "",
    val createdAt: Long,
    @androidx.room.ColumnInfo(defaultValue = "PENDING") val reviewStatus: String = "PENDING"
)
