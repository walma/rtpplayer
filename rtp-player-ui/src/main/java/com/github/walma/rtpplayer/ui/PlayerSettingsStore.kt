package com.github.walma.rtpplayer.ui

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playerSettingsDataStore by preferencesDataStore(name = "player_settings")

internal data class PersistedPlayerSettings(
    val streamUri: String,
    val networkCaching: String,
    val clockJitter: String,
    val clockSynchro: String,
    val demux: String,
    val recordingsFolderUri: Uri?,
)

internal class PlayerSettingsStore(
    private val context: Context,
) {
    fun settingsFlow(defaults: PersistedPlayerSettings): Flow<PersistedPlayerSettings> {
        return context.playerSettingsDataStore.data.map { preferences ->
            PersistedPlayerSettings(
                streamUri = preferences[Keys.STREAM_URI] ?: defaults.streamUri,
                networkCaching = preferences[Keys.NETWORK_CACHING]?.toString() ?: defaults.networkCaching,
                clockJitter = preferences[Keys.CLOCK_JITTER]?.toString() ?: defaults.clockJitter,
                clockSynchro = preferences[Keys.CLOCK_SYNCHRO]?.toString() ?: defaults.clockSynchro,
                demux = preferences[Keys.DEMUX] ?: defaults.demux,
                recordingsFolderUri = preferences[Keys.RECORDINGS_FOLDER_URI]?.let(Uri::parse)
                    ?: defaults.recordingsFolderUri,
            )
        }
    }

    suspend fun save(settings: PersistedPlayerSettings) {
        context.playerSettingsDataStore.edit { preferences ->
            preferences[Keys.STREAM_URI] = settings.streamUri
            preferences[Keys.NETWORK_CACHING] = settings.networkCaching.toIntOrNull() ?: 500
            preferences[Keys.CLOCK_JITTER] = settings.clockJitter.toIntOrNull() ?: 0
            preferences[Keys.CLOCK_SYNCHRO] = settings.clockSynchro.toIntOrNull() ?: 0
            preferences[Keys.DEMUX] = settings.demux

            if (settings.recordingsFolderUri != null) {
                preferences[Keys.RECORDINGS_FOLDER_URI] = settings.recordingsFolderUri.toString()
            } else {
                preferences.remove(Keys.RECORDINGS_FOLDER_URI)
            }
        }
    }

    suspend fun migrateLegacyRecordingFolderIfNeeded() {
        val preferences = context.playerSettingsDataStore.data.first()
        if (preferences.contains(Keys.RECORDINGS_FOLDER_URI)) {
            return
        }

        val legacyUri = RecordingStorage.getLegacySelectedFolderUri(context) ?: return
        context.playerSettingsDataStore.edit { updated ->
            updated[Keys.RECORDINGS_FOLDER_URI] = legacyUri.toString()
        }
        RecordingStorage.clearLegacySelectedFolder(context)
    }

    private object Keys {
        val STREAM_URI = stringPreferencesKey("stream_uri")
        val NETWORK_CACHING = intPreferencesKey("network_caching")
        val CLOCK_JITTER = intPreferencesKey("clock_jitter")
        val CLOCK_SYNCHRO = intPreferencesKey("clock_synchro")
        val DEMUX = stringPreferencesKey("demux")
        val RECORDINGS_FOLDER_URI = stringPreferencesKey("recordings_folder_uri")
    }
}
