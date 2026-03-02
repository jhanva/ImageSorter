package com.smartfolder.domain.model

import android.net.Uri

data class ImageInfo(
    val id: Long = 0,
    val folderId: Long,
    val uri: Uri,
    val displayName: String,
    val contentHash: String,
    val sizeBytes: Long,
    val lastModified: Long
)
