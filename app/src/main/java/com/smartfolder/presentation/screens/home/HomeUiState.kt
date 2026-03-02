package com.smartfolder.presentation.screens.home

import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.IndexingProgress
import com.smartfolder.domain.model.ModelChoice

data class HomeUiState(
    val referenceFolder: Folder? = null,
    val unsortedFolder: Folder? = null,
    val modelChoice: ModelChoice = ModelChoice.FAST,
    val refIndexingProgress: IndexingProgress = IndexingProgress(),
    val unsortedIndexingProgress: IndexingProgress = IndexingProgress(),
    val isIndexingRef: Boolean = false,
    val isIndexingUnsorted: Boolean = false,
    val canAnalyze: Boolean = false,
    val error: String? = null
)
