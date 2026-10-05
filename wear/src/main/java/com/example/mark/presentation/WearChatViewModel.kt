package com.example.mark.presentation

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mark.WearApplication
import com.example.mark.model.Message
import com.example.mark.router.ReplyMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WearUiState(
    val isPreparing: Boolean = false,
    val isListening: Boolean = false,
    val isLoading: Boolean = false,
    val isSpeaking: Boolean = false,
    val streamingReply: String? = null,
    val errorMessage: String? = null
)

class WearChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WearApplication
    private val settingsRepository = app.settingsRepository
    private val health by lazy { app.healthProvider }
    private val assistant by lazy { app.assistant }
    private val speech by lazy { app.speechHelper }
    private val tts by lazy { app.ttsManager }

    private var tStart = 0L
    @Volatile private var starting = false
    private var startTimeoutJob: Job? = null

    private val _uiState = MutableStateFlow(WearUiState())
    val uiState: StateFlow<WearUiState> = _uiState.asStateFlow()

    val liveTranscript: StateFlow<String> = speech.partialResults

    private val _liveReply = MutableStateFlow("")
    val liveReply: StateFlow<String> = _liveReply.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var sendJob: Job? = null
    private var streamJob: Job? = null
    private var consecutiveSilences = 0
    private val restartTimes = mutableListOf<Long>()
    private var proactiveSpokenThisSession = false
    private var lastBatteryLevel = -1
    private var batteryHysteresis = false
    private var criticalBatterySpoken = false

    // Registered on the application context, so it outlives this ViewModel
    // unless unregistered in onCleared(). Declared above init, which assigns it.
    private var batteryReceiver: android.content.BroadcastReceiver? = null

    private companion object {
        // Word reveal cadence for the on-screen reply. 380ms per word made the
        // text crawl and fall far behind the speech; ~90ms reads as continuous
        // while still feeling like it is being typed out.
        const val WORD_REVEAL_MS = 90L
    }

    init {
        // Observe the TTS speaking state and update UI
        viewModelScope.launch {
            tts.isSpeaking.collect { speaking ->
                val wasSpeaking = _uiState.value.isSpeaking
                _uiState.update { it.copy(isSpeaking = speaking) }

                // Automate flow: restart listening after speaking finishes
                if (wasSpeaking && !speaking && !starting) {
                    android.util.Log.d("MarkSession", "auto-restart (after speaking)")
                    startListening()
                }
            }
        }

        // Handle auto-greeting or morning brief on launch
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val fiveMinutes = 5 * 60 * 1000L
            if (app.lastSessionEndTime == 0L || (now - app.lastSessionEndTime) > fiveMinutes) {
                if (!checkMorningBrief()) {
                    playGreeting()
                }
            } else {
                android.util.Log.d("MarkGreet", "greeting-skipped (reopened within 5 min)")
                startListening()
            }
        }

        registerBatteryReceiver()
    }

    private fun checkMorningBrief(): Boolean {
        val prefs = app.getSharedPreferences("proactive_prefs", android.content.Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastBrief = prefs.getString("lastBriefDate", "")

        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)

        if (today != lastBrief && hour in 5..11) {
            viewModelScope.launch {
                val brief = buildMorningBrief()
                android.util.Log.d("MarkBrief", "playing morning brief")
                // Only a brief that was actually spoken counts as today's brief.
                if (speakProactive(brief)) prefs.edit().putString("lastBriefDate", today).apply()
            }
            return true
        }
        return false
    }

    private suspend fun buildMorningBrief(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val salutation = when (hour) {
            in 5..11 -> "Good morning sir"
            in 12..16 -> "Good afternoon sir"
            else -> "Good evening sir"
        }

        val briefParts = mutableListOf<String>()
        val cache = app.weatherCache
        if (cache != null && (System.currentTimeMillis() - cache.timestamp) < 30 * 60 * 1000L) {
            briefParts.add("${cache.tempCelsius} degrees outside, ${cache.conditionShort}")
        }

        // Say "no events" only when the calendar could actually be read;
        // without permission it used to claim an empty day every morning.
        val calendarReadable = canReadCalendar()
        val nextEvent = if (calendarReadable) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { fetchNextCalendarEvent() }.getOrNull()
            }
        } else null
        if (calendarReadable) briefParts.add(nextEvent ?: "Aaj koi event nahi hai")

        val battery = getBatteryLevel()
        if (battery < 40) briefParts.add("Battery $battery percent")

        val text = "$salutation. ${briefParts.joinToString(". ")}."
        android.util.Log.d("MarkBrief", "parts: weather=${cache != null}, calendar=${nextEvent != null}, battery=${battery < 40}")
        return text
    }

    private fun playGreeting() {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val salutation = when (hour) {
            in 5..11 -> "Good morning sir"
            in 12..16 -> "Good afternoon sir"
            else -> "Good evening sir"
        }

        var greeting = salutation
        val cache = app.weatherCache
        if (cache != null && (System.currentTimeMillis() - cache.timestamp) < 30 * 60 * 1000L) {
            greeting += ". ${cache.tempCelsius} degrees outside, ${cache.conditionShort}."
            android.util.Log.d("MarkGreet", "greeting-played (with weather)")
        } else {
            android.util.Log.d("MarkGreet", "greeting-played (no weather)")
        }

        tts.speak(greeting)
    }

    private fun registerBatteryReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_BATTERY_CHANGED)
            addAction(android.content.Intent.ACTION_POWER_CONNECTED)
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    android.content.Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                        if (level < 0 || scale <= 0) return
                        handleBatteryChange(level * 100 / scale)
                    }
                    android.content.Intent.ACTION_POWER_CONNECTED -> {
                        val vibrator = app.getSystemService(Vibrator::class.java)
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    }
                }
            }
        }
        app.registerReceiver(receiver, filter)
        batteryReceiver = receiver
    }

    private fun handleBatteryChange(pct: Int) {
        if (pct == lastBatteryLevel) return
        lastBatteryLevel = pct
        if (pct > 25) batteryHysteresis = false
        // The critical warning is allowed even after another proactive line.
        if (pct <= 5 && !criticalBatterySpoken) {
            if (speakProactive("Sir, battery bahut kam hai, sirf $pct percent.", critical = true)) criticalBatterySpoken = true
        } else if (pct in 6..15 && !batteryHysteresis && !proactiveSpokenThisSession) {
            batteryHysteresis = true
            speakProactive("Sir, battery $pct percent hai.")
        }
    }

    /** @return true if the line was actually spoken. */
    private fun speakProactive(text: String, critical: Boolean = false): Boolean {
        // tts.isSpeaking, not the UI copy: at launch the greeting has been queued
        // but the UI state has not caught up, and a sticky battery broadcast
        // used to cut the greeting off mid-word.
        if (_uiState.value.isListening || _uiState.value.isLoading || tts.isSpeaking.value) return false
        if (proactiveSpokenThisSession && !critical) return false
        proactiveSpokenThisSession = true
        tts.speak(text)
        return true
    }

    private fun canReadCalendar(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun fetchNextCalendarEvent(): String? {
        if (!canReadCalendar()) return null

        val now = System.currentTimeMillis()
        val endOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
        }.timeInMillis

        val projection = arrayOf(android.provider.CalendarContract.Events.TITLE, android.provider.CalendarContract.Events.DTSTART)
        val selection = "(${android.provider.CalendarContract.Events.DTSTART} >= ?) AND (${android.provider.CalendarContract.Events.DTSTART} <= ?)"
        val selectionArgs = arrayOf(now.toString(), endOfDay.toString())

        return app.contentResolver.query(
            android.provider.CalendarContract.Events.CONTENT_URI,
            projection, selection, selectionArgs, "${android.provider.CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val title = cursor.getString(0)
                val start = cursor.getLong(1)
                // "h baje" as one pattern threw: 'b' is not a valid pattern letter.
                val hour = java.text.SimpleDateFormat("h", java.util.Locale.getDefault()).format(java.util.Date(start))
                "$title $hour baje hai"
            } else null
        }
    }

    private fun getBatteryLevel(): Int {
        val intent = app.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        // Unknown level reads as full, so the brief never invents a low-battery warning.
        if (level < 0 || scale <= 0) return 100
        return level * 100 / scale
    }

    // NOTE: the old ensureVoiceSet() forced tts.setVoice("en-us-x-iol-local") before
    // every greeting and every send. That silently overrode the British voice chosen
    // in TextToSpeechManager — which is why the accent kept reverting to en-US.
    // Voice selection now lives in exactly one place: TextToSpeechManager.

    /**
     * SpeechRecognizerHelper already converts rmsdB to a 0..1 value, so the old
     * ((rms + 2) / 12) here squashed everything into 0.17..0.25 — the orb received
     * an almost constant value no matter how loud the speech was, which is why it
     * looked dead while listening. Only smoothing is applied now.
     *
     * Attack is faster than decay so peaks register instantly but fall away softly.
     */
    private fun updateAmplitude(amp: Float) {
        val v = amp.coerceIn(0f, 1f)
        val prev = _amplitude.value
        _amplitude.value = if (v > prev) prev * 0.4f + v * 0.6f else prev * 0.82f + v * 0.18f
    }

    fun startListening() {
        if (starting) return
        starting = true
        val now = System.currentTimeMillis()
        restartTimes.add(now)
        restartTimes.removeAll { now - it > 2000 }
        if (restartTimes.size >= 3) { endSession("error-loop"); return }

        startTimeoutJob?.cancel()
        startTimeoutJob = viewModelScope.launch {
            delay(8000)
            if (starting) {
                starting = false
                _uiState.update { it.copy(isPreparing = false, isListening = false) }
            }
        }

        if (_uiState.value.isListening || _uiState.value.isLoading) {
            if (_uiState.value.isLoading || _uiState.value.isSpeaking) {
                assistant.stop()
                tts.stop()
                _uiState.update { it.copy(isLoading = false, isSpeaking = false) }
            } else {
                starting = false
                startTimeoutJob?.cancel()
                return
            }
        }

        _uiState.update { it.copy(isPreparing = true, isListening = false) }
        tStart = System.currentTimeMillis()
        tts.stop()

        viewModelScope.launch {
            if (_uiState.value.isSpeaking) { tts.stop(); delay(350) }
            speech.startListening(
                onReady = {
                    _uiState.update { it.copy(isPreparing = false, isListening = true) }
                    val vibrator = app.getSystemService(Vibrator::class.java)
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    android.util.Log.d("MarkGreet", "mic-ready in ${System.currentTimeMillis() - tStart}ms")
                },
                onPartial = { },
                onResult = { finalText -> consecutiveSilences = 0; send(finalText) },
                onError = { message ->
                    if (message.contains("Didn't catch that") || message.contains("No speech detected")) {
                        consecutiveSilences++
                        if (consecutiveSilences >= 2) endSession("silence")
                        else { starting = false; startListening() }
                    } else {
                        _uiState.update { it.copy(errorMessage = null) }
                        tts.speak(message)
                        starting = false
                    }
                },
                onDone = { _uiState.update { it.copy(isListening = false) }; _amplitude.value = 0f; starting = false },
                onAmplitude = { amp -> updateAmplitude(amp) }
            )
        }
    }

    fun stopListening() { endSession("tap") }

    private fun endSession(reason: String) {
        android.util.Log.d("MarkSession", "ended ($reason)")
        app.lastSessionEndTime = System.currentTimeMillis()
        speech.stopListening()
        _uiState.update { it.copy(isListening = false, isPreparing = false, isLoading = false, isSpeaking = false) }
        consecutiveSilences = 0
        starting = false
        proactiveSpokenThisSession = false
        viewModelScope.launch { assistant.clearConversation() }
    }

    private fun send(text: String) {
        if (text.isBlank()) return
        _messages.update { it + Message(role = "user", content = text) }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        _liveReply.value = ""

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            var fullReply = ""
            var currentMode = ReplyMode.SPEAK
            assistant.send(text).collect { event ->
                when (event) {
                    is com.example.mark.assistant.AssistantEvent.Text -> {
                        currentMode = event.mode
                        fullReply += event.content
                        _uiState.update { it.copy(isLoading = false, streamingReply = fullReply) }
                        if (currentMode == ReplyMode.SPEAK) { tts.speakStream(event.content); startReplyStreaming(fullReply) }
                    }
                    is com.example.mark.assistant.AssistantEvent.Error -> {
                        _uiState.update { it.copy(isLoading = false, streamingReply = null) }
                        // Never read raw exception text aloud.
                        tts.speak(spokenError(event.throwable))
                    }
                    is com.example.mark.assistant.AssistantEvent.Done -> {
                        _uiState.update { it.copy(isLoading = false, streamingReply = null) }
                        // Show the complete reply immediately on finish. Waiting for
                        // the word-by-word reveal to catch up meant long answers were
                        // cut off the moment the turn ended.
                        if (fullReply.isNotBlank()) {
                            streamJob?.cancel()
                            _liveReply.value = fullReply.trim()
                        }
                        if (fullReply.isNotBlank()) {
                            _messages.update { it + Message(role = "assistant", content = fullReply) }
                            if (currentMode == ReplyMode.SPEAK) tts.finalizeStream()
                            else { fireDoubleHaptic(); android.util.Log.d("MarkSession", "auto-restart (after silent confirm)"); startListening() }

                            if (fullReply.contains("Theek hai sir", ignoreCase = true) ||
                                fullReply.contains("Good night sir", ignoreCase = true) ||
                                fullReply.contains("As you wish", ignoreCase = true)) {
                                viewModelScope.launch { delay(1500); endSession("END_SESSION") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun spokenError(t: Throwable): String {
        val msg = t.message.orEmpty()
        return when {
            "API key" in msg -> "Sir, my online brain isn't set up yet."
            t is java.io.IOException -> "Sir, I can't reach the internet right now."
            else -> "Sorry sir, something went wrong."
        }
    }

    private fun fireDoubleHaptic() {
        val vibrator = app.getSystemService(Vibrator::class.java)
        viewModelScope.launch {
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            delay(120)
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /**
     * Reveals the reply word by word. Only ever reveals words not shown yet, so a
     * new chunk arriving mid-reveal continues from where the text is rather than
     * restarting the cadence.
     */
    private fun startReplyStreaming(fullText: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val words = fullText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val shown = _liveReply.value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size

            // A long reply revealed at a fixed cadence finishes well after the
            // speech does, and the tail was being dropped when the turn ended.
            // Speed scales with length so text and voice land together.
            val perWord = when {
                words.size > 24 -> 35L
                words.size > 12 -> 55L
                else -> WORD_REVEAL_MS
            }

            for (i in shown until words.size) {
                _liveReply.value = words.take(i + 1).joinToString(" ")
                delay(perWord)
            }
            // Whatever happens with timing, the full text ends up on screen.
            _liveReply.value = fullText.trim()
        }
    }

    fun errorShown() { _uiState.update { it.copy(errorMessage = null) } }

    override fun onCleared() {
        batteryReceiver?.let { runCatching { app.unregisterReceiver(it) } }
        batteryReceiver = null
        speech.stopListening()
        tts.stop()
        super.onCleared()
    }

    private fun String.stripMarkdown() = replace(Regex("[*_`#]"), "")
}