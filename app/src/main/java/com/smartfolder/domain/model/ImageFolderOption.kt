package com.smartfolder.domain.model

data class ImageFolderOption(
    val displayName: String,
    val documentId: String,
    val imageCount: Int,
    /** Absolute directory path, used to move files without a SAF grant. */
    val absolutePath: String = ""
)
