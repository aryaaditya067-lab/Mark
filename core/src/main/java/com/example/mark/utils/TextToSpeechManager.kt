package com.example.mark.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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

    /** The offline voice picked at start-up; the fallback when an online voice can't be reached. */
    @Volatile private var localVoice: Voice? = null

    private val _isSpeaking = MutableStateFlow(false)

    /**
     * True from the first queued utterance until the last one finishes AND the
     * reply stream is closed. Tracking single utterances made this flicker to
     * false between sentences of a streamed reply — and the watch treats that
     * edge as "reply finished", restarts the mic and cuts the rest off.
     */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val progress = SpokenProgress()
    private val _spoken = MutableStateFlow("")

    /**
     * The words of the current reply heard so far, moving with the voice
     * (word by word where the engine reports it, else sentence by sentence).
     * Empty again when a new reply starts or speech is stopped.
     */
    val spoken: StateFlow<String> = _spoken.asStateFlow()

    private val inFlight = mutableSetOf<String>()
    @Volatile private var streamOpen = false
    private var utteranceCounter = 0

    private var buffer = ""
    private var spokeThisStream = false
    /** False when the text held before the engine was ready includes a filler. */
    private var earlyShown = true

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

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Spoken replies are assistant speech: music ducks under them instead of fighting them. */
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAttributes)
        .build()

    @Volatile private var holdingFocus = false

    /** The engine reported failure; nothing will ever be spoken, so nothing may wait on speech. */
    @Volatile private var initFailed = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                initFailed = true
                // Nothing will ever be spoken; never leave callers waiting on it.
                synchronized(inFlight) { inFlight.clear(); streamOpen = false; buffer = "" }
                _isSpeaking.value = false
            }
            if (ready) {
                tts?.setSpeechRate(0.90f)
                tts?.setPitch(0.85f)
                tts?.setAudioAttributes(speechAttributes)

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

                localVoice = selected
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
                        progress.started(utteranceId)?.let { _spoken.value = it }
                    }
                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        progress.range(utteranceId, end)?.let { _spoken.value = it }
                    }
                    override fun onDone(utteranceId: String?) {
                        progress.finished(utteranceId, completed = true)?.let { _spoken.value = it }
                        finished(utteranceId)
                    }
                    @Deprecated("deprecated")
                    override fun onError(utteranceId: String?) = stopped(utteranceId)
                    override fun onError(utteranceId: String?, errorCode: Int) = stopped(utteranceId)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = stopped(utteranceId)
                })

                pending?.let { speak(it) }
                pending = null
                // Streamed text that arrived before the engine was ready.
                val early = buffer.trim()
                buffer = ""
                if (early.isNotBlank()) enqueue(early, TextToSpeech.QUEUE_ADD, shown = earlyShown)
                earlyShown = true
                settle()
            }
        }
    }

    /**
     * English voices for the picker. Local ones always; online ("network")
     * voices only when [includeOnline] — they sound more natural on the phone,
     * but stall on the watch's Bluetooth link, so the watch never offers them.
     */
    fun availableVoices(includeOnline: Boolean = false): List<Voice> =
        tts?.voices
            ?.filter { it.locale.language == "en" && (it.name.endsWith("-local") || (includeOnline && it.name.endsWith("-network"))) }
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
     * An online voice with no network would go silent or stall, so speak with
     * the offline voice until the network is back, then return to the choice.
     */
    private fun ensureUsableVoice() {
        val engine = tts ?: return
        val wanted = desiredVoiceName?.let { name -> engine.voices?.find { it.name == name } }
        val target = when {
            wanted == null -> return
            !wanted.isNetworkConnectionRequired -> wanted
            isOnline() -> wanted
            else -> localVoice ?: return
        }
        if (engine.voice?.name != target.name) engine.voice = target
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
        _spoken.value = progress.reset()
        enqueue(text, TextToSpeech.QUEUE_FLUSH)
    }

    /**
     * Appends text to a buffer and speaks it as soon as a full sentence is ready.
     * Used for streaming LLM responses.
     */
    fun speakStream(chunk: String) {
        if (initFailed) return
        if (!ready) {
            // Held until the engine is ready (see init). It used to be dropped,
            // which lost the first reply after a cold start and left voice mode
            // stuck on "Thinking".
            if (!streamOpen) { _spoken.value = progress.reset(); earlyShown = true }
            buffer += chunk
            streamOpen = true
            _isSpeaking.value = true
            return
        }
        openStream()
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
     * Speaks a filler ("One moment, sir.") inside the reply stream. It is its
     * own utterance and is left out of [spoken]: it is not part of the reply.
     */
    fun speakFiller(text: String) {
        if (initFailed || text.isBlank()) return
        if (!ready) { speakStream(text.trim() + " "); earlyShown = false; return }
        openStream()
        // Whatever reply text is waiting goes first, so the order stays as sent.
        val waiting = buffer.trim()
        buffer = ""
        if (waiting.isNotBlank()) { spokeThisStream = true; enqueue(waiting, TextToSpeech.QUEUE_ADD) }
        enqueue(text.trim(), TextToSpeech.QUEUE_ADD, shown = false)
    }

    private fun openStream() {
        if (!streamOpen) {
            streamOpen = true
            spokeThisStream = false
            _spoken.value = progress.reset()
            _isSpeaking.value = true
        }
    }

    /**
     * Flush any remaining text in the stream buffer and close the stream.
     */
    fun finalizeStream() {
        streamOpen = false
        if (!ready) {
            if (initFailed) { buffer = ""; settle() }
            return // otherwise init speaks the held buffer once ready
        }
        val rest = buffer.trim()
        buffer = ""
        if (rest.isNotBlank()) enqueue(rest, TextToSpeech.QUEUE_ADD)
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
        _spoken.value = progress.reset()
        _isSpeaking.value = false
        releaseFocus()
    }

    private fun enqueue(text: String, mode: Int, shown: Boolean = true) {
        ensureAudible()
        ensureUsableVoice()
        if (!holdingFocus) {
            holdingFocus = true
            audioManager?.requestAudioFocus(focusRequest)
        }
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val id = "mark_tts_${utteranceCounter++}"
        synchronized(inFlight) { inFlight.add(id) }
        progress.queued(id, text, shown)
        _isSpeaking.value = true
        val result = tts?.speak(text, mode, params, id)
        if (result != TextToSpeech.SUCCESS) stopped(id)
    }

    private fun stopped(utteranceId: String?) {
        progress.finished(utteranceId, completed = false)
        finished(utteranceId)
    }

    private fun finished(utteranceId: String?) {
        synchronized(inFlight) { utteranceId?.let { inFlight.remove(it) } }
        settle()
    }

    /** Speaking ends only when nothing is queued and no more text is coming. */
    private fun settle() {
        val idle = synchronized(inFlight) { inFlight.isEmpty() && !streamOpen }
        if (idle) {
            _isSpeaking.value = false
            releaseFocus()
        }
    }

    private fun releaseFocus() {
        if (holdingFocus) {
            holdingFocus = false
            audioManager?.abandonAudioFocusRequest(focusRequest)
        }
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

    /**
     * Mark used to force media volume to 80% before every sentence — blasting
     * at night and leaving the user's music louder afterwards. Now the volume
     * is only raised when it is too low to hear at all.
     */
    private fun ensureAudible() {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val floor = (max + 3) / 4
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < floor) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, floor, 0)
        }
    }
}
