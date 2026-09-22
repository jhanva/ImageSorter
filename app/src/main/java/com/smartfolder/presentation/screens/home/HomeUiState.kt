package com.smartfolder.presentation.screens.home

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageFolderOption

data class HomeUiState(
    val sourceFolders: List<Folder> = emptyList(),
    val availableImageFolders: List<ImageFolderOption> = emptyList(),
    val isLoadingImageFolders: Boolean = false,
    /** Destinations are every image folder, which needs all files access. */
    val hasAllFilesAccess: Boolean = true,
    val canStartTriage: Boolean = false,
    val error: String? = null
)
