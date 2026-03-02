package com.smartfolder.domain.repository

import com.smartfolder.domain.model.ModelChoice
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val threshold: Flow<Float>
    val modelChoice: Flow<ModelChoice>
    val darkMode: Flow<Boolean>
    suspend fun setThreshold(value: Float)
    suspend fun setModelChoice(choice: ModelChoice)
    suspend fun setDarkMode(enabled: Boolean)
}
