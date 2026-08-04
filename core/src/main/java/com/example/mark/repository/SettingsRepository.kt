package com.example.mark.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Repository for managing user settings, such as dark mode preference and voice output.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val VOICE_OUTPUT = booleanPreferencesKey("voice_output")
        val VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val SAVED_PLACES = stringPreferencesKey("saved_places")
    }

    private val gson = com.google.gson.Gson()

    val savedPlaces: Flow<Map<String, String>> =
        context.dataStore.data.map { preferences ->
            val json = preferences[Keys.SAVED_PLACES] ?: "{}"
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyMap()
            }
        }

    suspend fun savePlace(label: String, address: String) {
        context.dataStore.edit { preferences ->
            val json = preferences[Keys.SAVED_PLACES] ?: "{}"
            val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, String>>() {}.type
            val map: MutableMap<String, String> = try {
                gson.fromJson(json, type)
            } catch (e: Exception) {
                mutableMapOf()
            }
            map[label.lowercase()] = address
            preferences[Keys.SAVED_PLACES] = gson.toJson(map)
        }
    }

    /**
     * Flow that emits the user's dark mode preference.
     */
    val isDarkMode: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DARK_MODE] ?: false }

    /**
     * Flow that emits whether voice output is enabled.
     */
    val voiceOutputEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.VOICE_OUTPUT] ?: false }

    /**
     * Flow that emits the selected TTS voice name.
     */
    val voiceName: Flow<String> =
        context.dataStore.data.map { it[Keys.VOICE_NAME] ?: "" }

    /**
     * Updates the dark mode preference.
     *
     * @param enabled True to enable dark mode, false to disable it.
     */
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    /**
     * Updates the voice output setting.
     *
     * @param enabled True to enable voice output.
     */
    suspend fun setVoiceOutput(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOICE_OUTPUT] = enabled }
    }

    /**
     * Updates the selected TTS voice name.
     *
     * @param name The name of the voice to use.
     */
    suspend fun setVoiceName(name: String) {
        context.dataStore.edit { it[Keys.VOICE_NAME] = name }
    }
}
