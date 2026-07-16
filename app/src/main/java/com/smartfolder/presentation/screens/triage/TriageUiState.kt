package com.smartfolder.presentation.screens.triage

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageInfo

data class TriageUiState(
    val isLoading: Boolean = true,
    val sourceFolder: Folder? = null,
    val destinations: List<Folder> = emptyList(),
    val queue: List<ImageInfo> = emptyList(),
    val currentIndex: Int = 0,
    val movedCount: Int = 0,
    val skippedCount: Int = 0,
    val deletedCount: Int = 0,
    val movedByDestination: Map<Long, Int> = emptyMap(),
    val canUndo: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val warning: String? = null
) {
    val current: ImageInfo? get() = queue.getOrNull(currentIndex)

    val isComplete: Boolean get() = !isLoading && current == null

    val totalCount: Int get() = queue.size

    val decidedCount: Int get() = movedCount + skippedCount + deletedCount

    val remainingCount: Int get() = (totalCount - currentIndex).coerceAtLeast(0)
}
