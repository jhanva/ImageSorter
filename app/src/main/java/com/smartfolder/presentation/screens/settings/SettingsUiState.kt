package com.smartfolder.presentation.screens.settings

import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.ModelChoice

enum class SettingsMessage {
    CACHE_CLEARED,
    CACHE_FAILED
}

data class SettingsUiState(
    val threshold: Float = 0.80f,
    val modelChoice: ModelChoice = ModelChoice.DEFAULT,
    val executionProfile: ExecutionProfile = ExecutionProfile.BALANCED,
    val darkMode: Boolean = false,
    val dynamicColor: Boolean = false,
    val isClearingCache: Boolean = false,
    val message: SettingsMessage? = null
)
