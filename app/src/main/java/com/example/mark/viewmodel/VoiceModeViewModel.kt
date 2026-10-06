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

    /** Set when the user said goodbye: end once the goodbye has been spoken. */
    private var endAfterSpeaking = false

    fun start() {
        if (_state.value.active) return
        assistant.stop()
        tts.stop()
        _state.value = VoiceModeState(active = true)
        consecutiveSilences = 0
        endAfterSpeaking = false
        if (!hasMicPermission()) {
            // Used to start listening anyway; every attempt then failed as
            // "silence" and voice mode quietly closed itself.
            _state.update { it.copy(phase = VoicePhase.IDLE, errorMessage = "I need microphone permission, sir.") }
            return
        }
        listen()
    }

    /**
     * Orb tap. While Mark is thinking or talking it cuts him off and listens
     * again (it used to close voice mode); otherwise it ends the session.
     */
    fun orbTapped() {
        val phase = _state.value.phase
        if (_state.value.active && (phase == VoicePhase.SPEAKING || phase == VoicePhase.THINKING)) {
            // Leave SPEAKING first, so tts.stop() is not mistaken for "finished
            // speaking" and the mic is not opened twice.
            _state.update { it.copy(phase = VoicePhase.LISTENING) }
            sendJob?.cancel()
            assistant.stop()
            tts.stop()
            consecutiveSilences = 0
            endAfterSpeaking = false
            listen()
        } else {
            stop()
        }
    }

    private fun hasMicPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun stop() {
        speech.cancel()
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
            // Anything handed to the TTS stream (a filler counts) must be closed
            // on every ending, or isSpeaking stays up and music stays ducked.
            var streamed = false
            assistant.send(text).collect { event ->
                when (event) {
                    is AssistantEvent.Text -> {
                        fullReply += event.content
                        _state.update { it.copy(lastReply = fullReply) }
                        streamed = true
                        tts.speakStream(event.content)
                    }
                    is AssistantEvent.EndSession -> endAfterSpeaking = true
                    // Spoken only; not part of the reply.
                    is AssistantEvent.Filler -> { streamed = true; tts.speakFiller(event.content) }
                    is AssistantEvent.Error -> {
                        // Cut any filler or partial answer; the error is shown instead.
                        if (streamed) { tts.stop(); streamed = false }
                        val msg = event.throwable.message ?: "Something went wrong."
                        _state.update { it.copy(phase = VoicePhase.IDLE, errorMessage = msg) }
                        delay(1500)
                        if (_state.value.active) listen()
                    }
                    is AssistantEvent.Done -> {
                        if (streamed) {
                            tts.finalizeStream()
                            // phase flips in the TTS callback
                        } else if (endAfterSpeaking) {
                            stop()
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
        if (endAfterSpeaking) {
            stop()
            return
        }
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
        speech.cancel()
        tts.stop()
        super.onCleared()
    }
}
