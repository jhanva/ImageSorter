package com.smartfolder.presentation.screens.home

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageFolderOption

data class HomeUiState(
    val destinationFolders: List<Folder> = emptyList(),
    val sourceFolders: List<Folder> = emptyList(),
    val availableImageFolders: List<ImageFolderOption> = emptyList(),
    val isLoadingImageFolders: Boolean = false,
    val canStartTriage: Boolean = false,
    val error: String? = null
)
