package com.smartfolder.presentation.screens.trash

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageInfo

data class TrashUiState(
    val isLoading: Boolean = true,
    val sourceFolder: Folder? = null,
    val items: List<ImageInfo> = emptyList(),
    val restoredCount: Int = 0,
    val isBusy: Boolean = false,
    val error: String? = null
)
