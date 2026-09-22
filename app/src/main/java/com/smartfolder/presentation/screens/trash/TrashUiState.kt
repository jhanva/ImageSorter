package com.smartfolder.presentation.screens.trash

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageInfo

data class TrashUiState(
    val isLoading: Boolean = true,
    val sourceFolder: Folder? = null,
    val items: List<ImageInfo> = emptyList(),
    /** Where the staged images physically live, shown so the folder is findable. */
    val trashFolderPaths: List<String> = emptyList(),
    val restoredCount: Int = 0,
    val isBusy: Boolean = false,
    val error: String? = null
)
