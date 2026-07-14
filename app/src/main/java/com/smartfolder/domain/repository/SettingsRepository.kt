package com.smartfolder.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val darkMode: Flow<Boolean>
    val dynamicColor: Flow<Boolean>
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setDynamicColor(enabled: Boolean)
}
