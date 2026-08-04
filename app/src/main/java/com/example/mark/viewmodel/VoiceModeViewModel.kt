package com.example.mark.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mark.MarkApplication
import com.example.mark.assistant.AssistantEvent
import com.example.mark.assistant.MarkAssistant
import com.example.mark.model.VoiceModeState
import com.example.mark.model.VoicePhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceModeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MarkApplication
    private val assistant = MarkAssistant.get(application.applicationContext)
    private val speech = app.speechHelper
    private val tts = app.ttsManager

    private val _state = MutableStateFlow(VoiceModeState())
    val state: StateFlow<VoiceModeState> = _state.asStateFlow()

    init {
        // Observe the TTS speaking state and update phase
        viewModelScope.launch {
            tts.isSpeaking.collect { speaking ->
                if (speaking) {
                    _state.update { it.copy(phase = VoicePhase.SPEAKING) }
                } else if (_state.value.phase == VoicePhase.SPEAKING) {
                    // Mark finished talking. Reopen the microphone for the next turn.
                    onSpeakingFinished()
                }
            }
        }
    }

    /** Two consecutive silences end the session, rather than spinning forever. */
    private var consecutiveSilences = 0

    fun start() {
        if (_state.value.active) return
        assistant.stop()
        tts.stop()
        _state.value = VoiceModeState(active = true)
        consecutiveSilences = 0
        listen()
    }

    fun stop() {
        speech.stopListening()
        tts.stop()
        _state.value = VoiceModeState(active = false)
    }

    // --- the loop ---

    private fun listen() {
        if (!_state.value.active) return

        _state.update {
            it.copy(phase = VoicePhase.LISTENING, transcript = "", errorMessage = null)
        }

        speech.startListening(
            onPartial = { partial -> _state.update { it.copy(transcript = partial) } },
            onResult = { text ->
                consecutiveSilences = 0
                send(text)
            },
            onError = { message -> onSilence(message) },
            onDone = { /* phase moves on in onResult or onError */ },
            onAmplitude = { amp -> _state.update { it.copy(amplitude = amp) } }
        )
    }

    private var sendJob: Job? = null

    private fun send(text: String) {
        if (text.isBlank()) {
            onSilence("Didn't catch that.")
            return
        }

        _state.update {
            it.copy(phase = VoicePhase.THINKING, transcript = text, amplitude = 0f)
        }

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            var fullReply = ""
            assistant.send(text).collect { event ->
                when (event) {
                    is AssistantEvent.Text -> {
                        fullReply += event.content
                        _state.update { it.copy(lastReply = fullReply) }
                        tts.speakStream(event.content)
                    }
                    is AssistantEvent.Error -> {
                        val msg = event.throwable.message ?: "Something went wrong."
                        _state.update { it.copy(phase = VoicePhase.IDLE, errorMessage = msg) }
                        delay(1500)
                        if (_state.value.active) listen()
                    }
                    is AssistantEvent.Done -> {
                        if (fullReply.isNotBlank()) {
                            tts.finalizeStream()
                            // phase flips in the TTS callback
                        } else if (_state.value.active) {
                            listen()
                        }
                    }
                }
            }
        }
    }

    private fun onSpeakingFinished() {
        if (!_state.value.active) return
        viewModelScope.launch {
            // The TTS engine holds audio focus for a moment after it stops.
            // Opening the mic too early makes Mark hear his own voice.
            delay(400)
            if (_state.value.active) listen()
        }
    }

    private fun onSilence(message: String) {
        consecutiveSilences++
        if (consecutiveSilences >= 2) {
            _state.update { it.copy(active = false, phase = VoicePhase.IDLE) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(errorMessage = message) }
            delay(800)
            if (_state.value.active) listen()
        }
    }

    override fun onCleared() {
        speech.stopListening()
        tts.stop()
        super.onCleared()
    }

    private fun String.stripMarkdown() = replace(Regex("[*_`#]"), "")
}
