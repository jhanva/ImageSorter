package com.smartfolder.data.repository

import com.smartfolder.data.local.datastore.SettingsDataStore
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {

    override val threshold: Flow<Float> = settingsDataStore.threshold

    override val modelChoice: Flow<ModelChoice> = settingsDataStore.modelChoice

    override val darkMode: Flow<Boolean> = settingsDataStore.darkMode

    override suspend fun setThreshold(value: Float) {
        settingsDataStore.setThreshold(value)
    }

    override suspend fun setModelChoice(choice: ModelChoice) {
        settingsDataStore.setModelChoice(choice)
    }

    override suspend fun setDarkMode(enabled: Boolean) {
        settingsDataStore.setDarkMode(enabled)
    }
}
