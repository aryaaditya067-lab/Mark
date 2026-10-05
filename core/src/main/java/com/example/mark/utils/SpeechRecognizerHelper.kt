package com.example.mark.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin wrapper around Android's SpeechRecognizer.
 * Optimized to reduce cold start latency and silence delays.
 */
class SpeechRecognizerHelper(private val context: Context) {

    // 1. Build the recognizer ONCE to avoid 1-2s cold start per call.
    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.util.Log.d("MarkDiag", "onDeviceRecognition = ${SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}")
            } else {
                android.util.Log.d("MarkDiag", "onDeviceRecognition = unsure (API < 31)")
            }
            externalRecognizer(context)
                ?.let { SpeechRecognizer.createSpeechRecognizer(context, it) }
                ?: SpeechRecognizer.createSpeechRecognizer(context)
        } else null

    private companion object {
        const val OWN_CANCEL_WINDOW_MS = 1500L
        const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"

        /**
         * A real recognition service from another app, Google's preferred.
         *
         * When Mark is the default digital assistant, Android makes Mark's own
         * (stub) RecognitionService the system default, and a plain
         * createSpeechRecognizer(context) would bind to it and hear nothing.
         * So the recognizer is always picked explicitly, never our own.
         */
        fun externalRecognizer(context: Context): ComponentName? {
            val services = context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .map { it.serviceInfo }
                .filter { it.packageName != context.packageName }
            val pick = services.firstOrNull { it.packageName == GOOGLE_PACKAGE } ?: services.firstOrNull()
            return pick?.let { ComponentName(it.packageName, it.name) }
        }
    }

    private val _partialResults = MutableStateFlow("")
    val partialResults: StateFlow<String> = _partialResults.asStateFlow()

    private var lastVoiceTime = 0L

    /** When we last cancelled the recognizer ourselves; its ERROR_CLIENT echo is ignored. */
    private var ownCancelAt = 0L
    private val SILENCE_THRESHOLD_MS = 2000L
    private val AMPLITUDE_THRESHOLD = 0.2f

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts listening for speech input.
     *
     * @param onReady   fires when the microphone is actually live
     * @param onPartial fires repeatedly while the user speaks
     * @param onResult  fires once with the final transcript
     * @param onError   fires with a human-readable message
     * @param onDone    fires when listening stops, for any reason
     */
    fun startListening(
        onReady: () -> Unit = {},
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit,
        onAmplitude: (Float) -> Unit = {}
    ) {
        android.util.Log.d("MarkSpeech", "startListening called. recognizer=$recognizer, available=${SpeechRecognizer.isRecognitionAvailable(context)}")
        
        val rec = recognizer ?: run {
            onError("Speech recognition is not available on this device.")
            onDone()
            return
        }

        // Reset state. cancel() echoes back as ERROR_CLIENT, which is ignored below.
        ownCancelAt = System.currentTimeMillis()
        rec.cancel()

        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                android.util.Log.d("MarkSpeech", "onResults: $text")
                _partialResults.value = ""
                // An empty result used to end listening with no result and no error,
                // which left phone voice mode stuck on "Listening" for good.
                if (text.isNotBlank()) onResult(text) else onError(errorText(SpeechRecognizer.ERROR_NO_MATCH))
                onDone()
            }

            override fun onPartialResults(partial: Bundle?) {
                partial
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let { 
                        android.util.Log.d("MarkSpeech", "onPartial: $it")
                        _partialResults.value = it
                        onPartial(it) 
                    }
            }

            override fun onError(error: Int) {
                _partialResults.value = ""
                if (error == SpeechRecognizer.ERROR_CLIENT &&
                    System.currentTimeMillis() - ownCancelAt < OWN_CANCEL_WINDOW_MS
                ) {
                    android.util.Log.d("MarkSpeech", "Ignoring Client Error from our own cancel()")
                    return
                }

                // Any other ERROR_CLIENT used to be swallowed too, without onDone,
                // so the caller waited forever. It is reported as a silence.
                val msg = errorText(if (error == SpeechRecognizer.ERROR_CLIENT) SpeechRecognizer.ERROR_NO_MATCH else error)
                android.util.Log.e("MarkSpeech", "onError: $error ($msg)")
                onError(msg)
                onDone()
            }

            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.d("MarkSpeech", "onReadyForSpeech")
                lastVoiceTime = System.currentTimeMillis()
                onReady()
            }

            override fun onBeginningOfSpeech() {
                android.util.Log.d("MarkSpeech", "onBeginningOfSpeech")
                lastVoiceTime = System.currentTimeMillis()
            }

            override fun onRmsChanged(rmsdB: Float) {
                val linear = (rmsdB / 8f).coerceIn(0f, 1f)
                val amp = kotlin.math.sqrt(linear)
                onAmplitude(amp)

                if (amp > AMPLITUDE_THRESHOLD) {
                    lastVoiceTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastVoiceTime > SILENCE_THRESHOLD_MS) {
                    rec.stopListening()
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")

            // Reduce silence timeouts for snappier response.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900L)
        }

        rec.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun shutdown() {
        recognizer?.destroy()
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Recognition cancelled."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
        SpeechRecognizer.ERROR_NETWORK -> "Network error."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy."
        SpeechRecognizer.ERROR_SERVER -> "Server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        else -> "Speech recognition failed."
    }
}
