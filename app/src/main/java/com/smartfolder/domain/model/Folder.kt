package com.smartfolder.domain.model

import android.net.Uri

data class Folder(
    val id: Long = 0,
    val uri: Uri,
    val displayName: String,
    val role: FolderRole,
    val imageCount: Int = 0,
    val indexedCount: Int = 0,
    val lastIndexedAt: Long? = null
)
