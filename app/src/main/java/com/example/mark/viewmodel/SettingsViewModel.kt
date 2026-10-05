package com.example.mark.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mark.assistant.MarkAssistant
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.tools.LaptopTool
import com.example.mark.model.MemoryFact
import com.example.mark.repository.MemoryStore
import com.example.mark.repository.SettingsRepository
import com.example.mark.utils.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen, handling UI state and user preferences.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    val ttsManager: TextToSpeechManager,
    private val memory: MemoryStore? = null,
    private val laptop: LaptopTool? = null
) : ViewModel() {

    // ---- Laptop ----

    private val _laptopConfig = MutableStateFlow(laptop?.config() ?: LaptopTool.Config("", "", ""))
    val laptopConfig: StateFlow<LaptopTool.Config> = _laptopConfig.asStateFlow()

    private val _laptopStatus = MutableStateFlow<String?>(null)
    /** Result of the last "Test connection", shown under the laptop fields. */
    val laptopStatus: StateFlow<String?> = _laptopStatus.asStateFlow()

    fun saveLaptop(host: String, token: String, mac: String) {
        val tool = laptop ?: return
        tool.configure(host, token, mac)
        _laptopConfig.value = tool.config()
        _laptopStatus.value = null
    }

    fun testLaptop() = viewModelScope.launch {
        val tool = laptop ?: return@launch
        _laptopStatus.value = "Checking…"
        val result = tool.execute(ToolRequest.of(mapOf("action" to "status")))
        _laptopStatus.value = if (result is ToolResult.Failure) result.text else "Laptop online, sir. ${result.text}"
    }

    /** What Mark should call the user. */
    val userName: StateFlow<String> = repository.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setUserName(name: String) = viewModelScope.launch {
        repository.setUserName(name)
        MarkAssistant.refreshSituation()
    }

    private val _facts = MutableStateFlow<List<MemoryFact>>(emptyList())

    /** Everything Mark has been asked to remember, newest first. */
    val facts: StateFlow<List<MemoryFact>> = _facts.asStateFlow()

    fun refreshFacts() = viewModelScope.launch {
        val store = memory ?: return@launch
        _facts.value = runCatching { store.all() }.getOrDefault(emptyList()).sortedByDescending { it.createdAt }
    }

    fun forget(fact: MemoryFact) = viewModelScope.launch {
        val store = memory ?: return@launch
        runCatching { store.remove(setOf(fact.id)) }
        refreshFacts()
    }

    /**
     * StateFlow representing the user's dark mode preference.
     */
    val isDarkMode: StateFlow<Boolean> = repository.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * StateFlow representing whether voice output is enabled.
     */
    val voiceOutputEnabled: StateFlow<Boolean> = repository.voiceOutputEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * StateFlow representing the selected TTS voice name.
     */
    val voiceName: StateFlow<String> = repository.voiceName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * Flow that emits the user's saved places.
     */
    val savedPlaces: StateFlow<Map<String, String>> = repository.savedPlaces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Updates the dark mode setting.
     *
     * @param enabled True to enable dark mode.
     */
    fun setDarkMode(enabled: Boolean) = viewModelScope.launch {
        repository.setDarkMode(enabled)
    }

    /**
     * Updates the voice output setting.
     *
     * @param enabled True to enable voice output.
     */
    fun setVoiceOutput(enabled: Boolean) = viewModelScope.launch {
        repository.setVoiceOutput(enabled)
    }

    /**
     * Updates the selected TTS voice name.
     *
     * @param name The name of the voice to use.
     */
    fun setVoiceName(name: String) = viewModelScope.launch {
        repository.setVoiceName(name)
    }

    /**
     * Updates a saved place.
     *
     * @param label The label of the place (e.g., "home").
     * @param address The address.
     */
    fun savePlace(label: String, address: String) = viewModelScope.launch {
        repository.savePlace(label, address)
    }
}
