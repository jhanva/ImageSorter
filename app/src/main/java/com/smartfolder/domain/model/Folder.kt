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

/**
 * Stable identity for a folder in the triage screen. Destinations are read from
 * the media index on every run instead of being stored, so they have no
 * database id: their uri is what identifies them.
 */
val Folder.key: String get() = uri.toString()
