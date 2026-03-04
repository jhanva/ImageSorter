package com.smartfolder.domain.repository

import com.smartfolder.domain.model.ExecutionProfile
import com.smartfolder.domain.model.ModelChoice
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val threshold: Flow<Float>
    val modelChoice: Flow<ModelChoice>
    val executionProfile: Flow<ExecutionProfile>
    val darkMode: Flow<Boolean>
    val manualMode: Flow<Boolean>
    suspend fun setThreshold(value: Float)
    suspend fun setModelChoice(choice: ModelChoice)
    suspend fun setExecutionProfile(profile: ExecutionProfile)
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setManualMode(enabled: Boolean)
}
