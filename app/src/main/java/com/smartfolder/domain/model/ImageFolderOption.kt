package com.smartfolder.domain.model

import android.net.Uri

data class ImageFolderOption(
    val displayName: String,
    val documentId: String,
    val imageCount: Int,
    /** Absolute directory path, used to move files without a SAF grant. */
    val absolutePath: String = "",
    /** Most recent image in the folder, shown as its thumbnail. */
    val coverUri: Uri? = null
)
