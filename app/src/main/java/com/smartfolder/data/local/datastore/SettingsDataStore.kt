package com.smartfolder.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartfolder.domain.model.ModelChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THRESHOLD = floatPreferencesKey("threshold")
        val MODEL_CHOICE = stringPreferencesKey("model_choice")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    val threshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.THRESHOLD] ?: 0.80f
    }

    val modelChoice: Flow<ModelChoice> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.MODEL_CHOICE] ?: ModelChoice.FAST.name
        try {
            ModelChoice.valueOf(name)
        } catch (e: IllegalArgumentException) {
            ModelChoice.FAST
        }
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    suspend fun setThreshold(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THRESHOLD] = value.coerceIn(0.70f, 0.95f)
        }
    }

    suspend fun setModelChoice(choice: ModelChoice) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MODEL_CHOICE] = choice.name
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }
}
