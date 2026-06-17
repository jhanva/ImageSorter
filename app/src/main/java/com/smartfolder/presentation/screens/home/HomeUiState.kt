package com.smartfolder.presentation.screens.home

import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageFolderOption
import com.smartfolder.domain.model.IndexingProgress
import com.smartfolder.domain.model.ModelChoice

data class HomeUiState(
    val destinationFolders: List<Folder> = emptyList(),
    val sourceFolders: List<Folder> = emptyList(),
    val modelChoice: ModelChoice = ModelChoice.FAST,
    val executionProfile: ExecutionProfile = ExecutionProfile.BALANCED,
    val destinationIndexingProgress: IndexingProgress = IndexingProgress(),
    val sourceIndexingProgress: IndexingProgress = IndexingProgress(),
    val isIndexingDestinations: Boolean = false,
    val isIndexingSources: Boolean = false,
    val availableImageFolders: List<ImageFolderOption> = emptyList(),
    val isLoadingImageFolders: Boolean = false,
    val canAnalyze: Boolean = false,
    val error: String? = null
)
