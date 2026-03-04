package com.smartfolder.presentation.screens.settings

import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.ModelChoice

data class SettingsUiState(
    val threshold: Float = 0.80f,
    val modelChoice: ModelChoice = ModelChoice.FAST,
    val executionProfile: ExecutionProfile = ExecutionProfile.BALANCED,
    val darkMode: Boolean = false,
    val manualMode: Boolean = false,
    val isClearingCache: Boolean = false,
    val message: String? = null
)
