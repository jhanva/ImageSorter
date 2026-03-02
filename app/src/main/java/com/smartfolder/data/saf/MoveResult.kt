package com.smartfolder.data.saf

sealed class MoveResult {
    data class Moved(val newUri: android.net.Uri) : MoveResult()
    data class CopiedOnly(val newUri: android.net.Uri, val reason: String) : MoveResult()
    data class Failure(val error: String) : MoveResult()
}
