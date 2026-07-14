package com.smartfolder.data.repository

import com.smartfolder.data.local.datastore.SettingsDataStore
import com.smartfolder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {

    override val darkMode: Flow<Boolean> = settingsDataStore.darkMode

    override val dynamicColor: Flow<Boolean> = settingsDataStore.dynamicColor

    override suspend fun setDarkMode(enabled: Boolean) {
        settingsDataStore.setDarkMode(enabled)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        settingsDataStore.setDynamicColor(enabled)
    }
}
