package com.smartfolder.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.triagePositions: DataStore<Preferences> by preferencesDataStore(
    name = "triage_positions"
)

/**
 * Remembers the last image shown per source folder so a triage session can
 * resume where the user left off.
 */
@Singleton
class TriagePositionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun key(folderId: Long) = stringPreferencesKey("last_uri_$folderId")

    suspend fun getLastImageUri(folderId: Long): String? {
        return context.triagePositions.data.first()[key(folderId)]
    }

    suspend fun setLastImageUri(folderId: Long, uri: String) {
        context.triagePositions.edit { prefs ->
            prefs[key(folderId)] = uri
        }
    }

    suspend fun clear(folderId: Long) {
        context.triagePositions.edit { prefs ->
            prefs.remove(key(folderId))
        }
    }
}
