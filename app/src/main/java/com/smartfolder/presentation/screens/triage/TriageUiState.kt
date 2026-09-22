package com.smartfolder.presentation.screens.triage

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.key
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
    val movedByDestination: Map<String, Int> = emptyMap(),
    /** Destinations already used in this session; they are shown first. */
    val usedDestinationKeys: Set<String> = emptySet(),
    /** Set while all files access is missing: no destination can be written. */
    val needsAllFilesAccess: Boolean = false,
    val canUndo: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val warning: String? = null
) {
    private val byName = compareBy(String.CASE_INSENSITIVE_ORDER, Folder::displayName)

    /**
     * Destinations already used, alphabetically. They sit at the top so a long
     * folder list does not have to be scrolled again for the ones in play.
     */
    val usedDestinations: List<Folder>
        get() = destinations.filter { it.key in usedDestinationKeys }.sortedWith(byName)

    /** The destinations not used yet, alphabetically. */
    val otherDestinations: List<Folder>
        get() = destinations.filterNot { it.key in usedDestinationKeys }.sortedWith(byName)

    val current: ImageInfo? get() = queue.getOrNull(currentIndex)

    val isComplete: Boolean get() = !isLoading && current == null

    val totalCount: Int get() = queue.size

    val decidedCount: Int get() = movedCount + skippedCount + deletedCount

    val remainingCount: Int get() = (totalCount - currentIndex).coerceAtLeast(0)
}
