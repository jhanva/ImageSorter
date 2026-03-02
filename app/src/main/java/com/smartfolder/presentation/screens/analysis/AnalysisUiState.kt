package com.smartfolder.presentation.screens.analysis

import com.smartfolder.domain.model.AnalysisProgress
import com.smartfolder.domain.model.SuggestionItem

data class AnalysisUiState(
    val progress: AnalysisProgress = AnalysisProgress(),
    val suggestions: List<SuggestionItem> = emptyList(),
    val isAnalyzing: Boolean = false,
    val error: String? = null
)
