package com.example.mark.utils

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps Android TextToSpeech. Initialisation is async, so speak() calls
 * made before the engine is ready are queued in [pending].
 *
 * Shared across the app as a singleton.
 */
class TextToSpeechManager(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var desiredVoiceName: String? = null

    private val _isSpeaking = MutableStateFlow(false)

    /**
     * True from the first queued utterance until the last one finishes AND the
     * reply stream is closed. Tracking single utterances made this flicker to
     * false between sentences of a streamed reply — and the watch treats that
     * edge as "reply finished", restarts the mic and cuts the rest off.
     */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val inFlight = mutableSetOf<String>()
    @Volatile private var streamOpen = false
    private var utteranceCounter = 0

    private var buffer = ""
    private var spokeThisStream = false

    private companion object {
        const val FIRST_SENTENCE_LENGTH = 12
        const val BATCH_SENTENCE_LENGTH = 80

        /**
         * Deterministic voice pick, most-wanted first. Google's voice names never
         * contain the word "male" — the variant code is the only reliable signal.
         * en-GB male variants: gbb, gbd. (gba/gbc are female; rjs is male but has
         * a strong regional accent, kept as a later fallback.)
         *
         * tts.voices is a Set — its iteration order CAN DIFFER between processes,
         * which is why "find first high-quality en-GB" picked a male voice in the
         * debug build and a female one in release. Never rely on Set order.
         */
        val VOICE_PRIORITY = listOf(
            "en-gb-x-gbb-local",
            "en-gb-x-gbd-local",
            "en-gb-x-rjs-local"
        )
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setSpeechRate(0.90f)
                tts?.setPitch(0.85f)

                val allVoices = tts?.voices
                allVoices?.forEach {
                    android.util.Log.d(
                        "MarkVoice",
                        "voice=${it.name} locale=${it.locale} quality=${it.quality} latency=${it.latency} needsNetwork=${it.isNetworkConnectionRequired}"
                    )
                }

                // 1. Exact pinned names, in priority order (deterministic).
                // 2. Else any local en-GB, sorted by name so the pick is stable.
                // 3. Else leave the engine default and set US locale.
                val selected = allVoices?.let { voices ->
                    VOICE_PRIORITY.firstNotNullOfOrNull { wanted ->
                        voices.find { it.name == wanted && !it.isNetworkConnectionRequired }
                    } ?: voices
                        .filter {
                            it.locale.language == "en" && it.locale.country == "GB" &&
                                    !it.isNetworkConnectionRequired
                        }
                        .sortedBy { it.name }
                        .firstOrNull()
                }

                selected?.let {
                    tts?.voice = it
                    android.util.Log.d("MarkVoice", "SELECTED ${it.name}")
                } ?: run {
                    android.util.Log.d("MarkVoice", "SELECTED none (no local en-GB voice installed)")
                    tts?.language = Locale.US
                    desiredVoiceName?.let { applyVoice(it) }
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }
                    override fun onDone(utteranceId: String?) = finished(utteranceId)
                    @Deprecated("deprecated")
                    override fun onError(utteranceId: String?) = finished(utteranceId)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
                })

                pending?.let { speak(it) }
                pending = null
            }
        }
    }

    /** Local voices only — network voices stall on the watch's Bluetooth link. */
    fun availableVoices(): List<Voice> =
        tts?.voices
            ?.filter { it.locale.language == "en" && it.name.endsWith("-local") }
            ?.sortedBy { it.name }
            .orEmpty()

    /** Safe to call before the engine is ready; applied on init. */
    fun setVoice(name: String) {
        desiredVoiceName = name
        if (ready) applyVoice(name)
    }

    private fun applyVoice(name: String) {
        tts?.voices?.find { it.name == name }?.let { tts?.voice = it }
    }

    /**
     * Speaks the given text out loud.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pending = text
            return
        }

        // A one-shot reply replaces whatever was queued or streaming.
        synchronized(inFlight) {
            inFlight.clear()
            streamOpen = false
            buffer = ""
        }
        enqueue(text, TextToSpeech.QUEUE_FLUSH)
    }

    /**
     * Appends text to a buffer and speaks it as soon as a full sentence is ready.
     * Used for streaming LLM responses.
     */
    fun speakStream(chunk: String) {
        if (!ready) return
        if (!streamOpen) {
            streamOpen = true
            spokeThisStream = false
            _isSpeaking.value = true
        }
        buffer += chunk

        // The first sentence goes out as soon as it is complete, so the reply
        // starts quickly; later ones are batched for smoother prosody.
        val minLength = if (spokeThisStream) BATCH_SENTENCE_LENGTH else FIRST_SENTENCE_LENGTH
        val cut = SpeechChunker.cutPoint(buffer, minLength)
        if (cut > 0) {
            val toSpeak = buffer.substring(0, cut).trim()
            buffer = buffer.substring(cut)
            if (toSpeak.isNotBlank()) {
                spokeThisStream = true
                enqueue(toSpeak, TextToSpeech.QUEUE_ADD)
            }
        }
    }

    /**
     * Flush any remaining text in the stream buffer and close the stream.
     */
    fun finalizeStream() {
        val rest = buffer.trim()
        buffer = ""
        if (rest.isNotBlank()) enqueue(rest, TextToSpeech.QUEUE_ADD)
        streamOpen = false
        settle()
    }

    /**
     * Stops any current speech.
     */
    fun stop() {
        synchronized(inFlight) {
            inFlight.clear()
            streamOpen = false
            buffer = ""
        }
        tts?.stop()
        _isSpeaking.value = false
    }

    private fun enqueue(text: String, mode: Int) {
        forceMaxVolume()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val id = "mark_tts_${utteranceCounter++}"
        synchronized(inFlight) { inFlight.add(id) }
        _isSpeaking.value = true
        val result = tts?.speak(text, mode, params, id)
        if (result != TextToSpeech.SUCCESS) finished(id)
    }

    private fun finished(utteranceId: String?) {
        synchronized(inFlight) { utteranceId?.let { inFlight.remove(it) } }
        settle()
    }

    /** Speaking ends only when nothing is queued and no more text is coming. */
    private fun settle() {
        val idle = synchronized(inFlight) { inFlight.isEmpty() && !streamOpen }
        if (idle) _isSpeaking.value = false
    }

    /**
     * Shuts down the TTS engine and releases resources.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        _isSpeaking.value = false
    }

    private fun forceMaxVolume() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.let {
            val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            it.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (max * 0.8f).toInt(),
                0
            )
        }
    }
}