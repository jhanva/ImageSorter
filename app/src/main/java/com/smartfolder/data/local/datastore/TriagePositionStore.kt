package com.smartfolder.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
 * resume where the user left off, plus the destinations already used in that
 * session so they keep their promoted position when the screen is reopened.
 */
@Singleton
class TriagePositionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun key(folderId: Long) = stringPreferencesKey("last_uri_$folderId")

    private fun usedKey(folderId: Long) = stringSetPreferencesKey("used_destinations_$folderId")

    suspend fun getLastImageUri(folderId: Long): String? {
        return context.triagePositions.data.first()[key(folderId)]
    }

    suspend fun setLastImageUri(folderId: Long, uri: String) {
        context.triagePositions.edit { prefs ->
            prefs[key(folderId)] = uri
        }
    }

    suspend fun getUsedDestinations(folderId: Long): Set<Long> {
        val stored = context.triagePositions.data.first()[usedKey(folderId)] ?: return emptySet()
        return stored.mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setUsedDestinations(folderId: Long, destinationIds: Set<Long>) {
        context.triagePositions.edit { prefs ->
            if (destinationIds.isEmpty()) {
                prefs.remove(usedKey(folderId))
            } else {
                prefs[usedKey(folderId)] = destinationIds.map { it.toString() }.toSet()
            }
        }
    }

    suspend fun clear(folderId: Long) {
        context.triagePositions.edit { prefs ->
            prefs.remove(key(folderId))
            prefs.remove(usedKey(folderId))
        }
    }
}
