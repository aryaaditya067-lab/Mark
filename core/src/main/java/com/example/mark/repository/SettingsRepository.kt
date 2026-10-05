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
        val USER_NAME = stringPreferencesKey("user_name")
        val BRIEF_ENABLED = booleanPreferencesKey("brief_enabled")
        val BRIEF_TIME = stringPreferencesKey("brief_time")
        val ANNOUNCE_MESSAGES = booleanPreferencesKey("announce_messages")
        val SHARE_SCHEDULE = booleanPreferencesKey("share_schedule")
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

    /** What Mark should call the user; blank when not set. */
    val userName: Flow<String> =
        context.dataStore.data.map { it[Keys.USER_NAME] ?: "" }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name.trim() }
    }

    /** Whether calendar and task titles are included with every AI question. */
    val shareSchedule: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SHARE_SCHEDULE] ?: true }

    suspend fun setShareSchedule(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHARE_SCHEDULE] = enabled }
    }

    /** Speak new chat messages into headphones; off unless the user turns it on. */
    val announceMessages: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ANNOUNCE_MESSAGES] ?: false }

    suspend fun setAnnounceMessages(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANNOUNCE_MESSAGES] = enabled }
    }

    /** Daily brief notification; off until the user turns it on. */
    val briefEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BRIEF_ENABLED] ?: false }

    /** "HH:mm", local time. */
    val briefTime: Flow<String> =
        context.dataStore.data.map { it[Keys.BRIEF_TIME] ?: "07:30" }

    suspend fun setBrief(enabled: Boolean, time: String) {
        context.dataStore.edit {
            it[Keys.BRIEF_ENABLED] = enabled
            it[Keys.BRIEF_TIME] = time
        }
    }

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
