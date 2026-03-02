package com.smartfolder.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val role: String, // "REFERENCE" or "UNSORTED"
    val imageCount: Int = 0,
    val indexedCount: Int = 0,
    val lastIndexedAt: Long? = null
)
