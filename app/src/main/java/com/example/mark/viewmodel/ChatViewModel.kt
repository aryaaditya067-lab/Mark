package com.example.mark.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mark.MarkApplication
import com.example.mark.assistant.AssistantEvent
import com.example.mark.assistant.MarkAssistant
import com.example.mark.model.ChatUiState
import com.example.mark.model.Message
import com.example.mark.repository.ChatHistoryRepository
import com.example.mark.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MarkApplication
    private val settingsRepository = SettingsRepository(application.applicationContext)
    private val historyRepository = ChatHistoryRepository.instance
    private val health = app.healthProvider
    private val assistant = MarkAssistant.get(application.applicationContext)
    private val speech = app.speechHelper
    private val tts = app.ttsManager

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    init {
        // Push a snapshot so the watch has something to read.
        viewModelScope.launch {
            runCatching {
                if (health.isAvailable()) {
                    val sleep = health.lastNightSleep()
                    com.example.mark.repository.HealthRepository.instance.write(
                        com.example.mark.model.HealthSnapshot(
                            steps = health.todaySteps(),
                            sleepMinutes = sleep?.sleepMinutes,
                            sleepStart = sleep?.sleepStart,
                            sleepEnd = sleep?.sleepEnd
                        )
                    )
                }
            }
        }

        // Observe the TTS speaking state and update UI
        viewModelScope.launch {
            tts.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }

        // Apply preferred voice
        viewModelScope.launch {
            settingsRepository.voiceName.collect { name ->
                if (name.isNotBlank()) tts.setVoice(name)
            }
        }
    }

    /**
     * onRmsChanged fires roughly every 16 ms and jitters hard. Feeding that
     * straight into a spring makes the orb vibrate rather than breathe.
     */
    private fun updateAmplitude(raw: Float) {
        _amplitude.value = _amplitude.value * 0.7f + (raw.coerceAtLeast(0f)) * 0.3f
    }

    /** Only the bubbles worth showing. Tool messages are filtered out. */
    val messages: StateFlow<List<Message>> = historyRepository.messages
        .map { list -> list.filter { it.isVisible } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // --- Voice input ---

    fun startListening() {
        if (_uiState.value.isListening || _uiState.value.isLoading) {
            // Barge-in: if we are loading (thinking/speaking), stop and listen again.
            if (_uiState.value.isLoading || _uiState.value.isSpeaking) {
                assistant.stop()
                tts.stop()
                _uiState.update { it.copy(isLoading = false, isSpeaking = false) }
            } else return
        }

        tts.stop()
        _uiState.update { it.copy(isListening = true, inputText = "") }

        speech.startListening(
            onPartial = { partial -> _uiState.update { it.copy(inputText = partial) } },
            onResult = { finalText ->
                _uiState.update { it.copy(inputText = finalText) }
                sendMessage()
            },
            onError = { message -> _uiState.update { it.copy(errorMessage = message) } },
            onDone = { 
                _uiState.update { it.copy(isListening = false) }
                _amplitude.value = 0f
            },
            onAmplitude = { amp -> updateAmplitude(amp) }
        )
    }

    fun stopListening() {
        speech.stopListening()
        _uiState.update { it.copy(isListening = false) }
        _amplitude.value = 0f
    }

    // --- Chat ---

    private var sendJob: Job? = null

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isLoading) return

        _uiState.update { it.copy(inputText = "", isLoading = true, errorMessage = null) }

        // A new question supersedes the reply being spoken; also closes its
        // TTS stream so the speaking state cannot stay stuck on.
        tts.stop()
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            var fullReply = ""
            val messageId = UUID.randomUUID().toString()
            
            assistant.send(text).collect { event ->
                when (event) {
                    is AssistantEvent.Text -> {
                        fullReply += event.content
                        _uiState.update { it.copy(isLoading = false) }
                        
                        // Upsert the assistant's message in the repository local flow for UI
                        _uiState.update { it.copy(streamingReply = fullReply) }

                        if (settingsRepository.voiceOutputEnabled.first()) {
                            tts.speakStream(event.content)
                        }
                    }
                    is AssistantEvent.EndSession -> { /* typed chat has no session to end */ }
                    // Spoken only (trailing space completes the sentence for TTS); never shown as the reply.
                    is AssistantEvent.Filler -> if (settingsRepository.voiceOutputEnabled.first()) tts.speakStream(event.content + " ")
                    is AssistantEvent.Error -> {
                        val msg = event.throwable.message ?: "Something went wrong."
                        _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
                    }
                    is AssistantEvent.Done -> {
                        _uiState.update { it.copy(isLoading = false, streamingReply = null) }
                        if (fullReply.isNotBlank()) {
                            if (settingsRepository.voiceOutputEnabled.first()) {
                                tts.finalizeStream()
                            }
                        }
                    }
                }
            }
        }
    }

    fun errorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearChat() {
        tts.stop()
        viewModelScope.launch {
            runCatching { assistant.clearConversation() }
        }
    }

    override fun onCleared() {
        speech.stopListening()
        tts.stop()
        super.onCleared()
    }

    private fun String.stripMarkdown() = replace(Regex("[*_`#]"), "")
}