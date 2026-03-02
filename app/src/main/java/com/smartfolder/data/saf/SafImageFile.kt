package com.smartfolder.data.saf

import android.net.Uri

data class SafImageFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String
)
