package com.example.mark.assist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.example.mark.MainActivity

/**
 * Lets the user pick Mark as the phone's digital assistant (Settings > Apps >
 * Default apps > Digital assistant). The assist gesture — long-press power or
 * home, or a corner swipe — then opens Mark straight into voice mode.
 *
 * Nothing here listens in the background: the system only starts the session
 * when the user makes the gesture.
 */
class MarkVoiceInteractionService : VoiceInteractionService()

class MarkVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = MarkVoiceSession(this)
}

class MarkVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        startAssistantActivity(MainActivity.voiceIntent(context))
        hide()
    }
}

/**
 * Required by the assistant declaration, and therefore made the system's
 * default recognizer while Mark is the assistant. Mark itself never uses it
 * (SpeechRecognizerHelper binds another app's recognizer explicitly); any
 * other app that asks gets a clean "busy" error instead of silence.
 */
class MarkRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}

/** Quick Settings tile: one tap from the notification shade into voice mode. */
class MarkTileService : android.service.quicksettings.TileService() {
    override fun onClick() {
        super.onClick()
        val intent = MainActivity.voiceIntent(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The plain-Intent overload throws on Android 14+.
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
