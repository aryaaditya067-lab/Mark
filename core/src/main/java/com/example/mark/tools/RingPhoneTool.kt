package com.example.mark.tools

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType
import kotlinx.coroutines.*

class RingPhoneTool(private val context: Context) : Tool {

    override val name = "ring_phone"
    override val intent = IntentType.RING_PHONE

    override val definition = FunctionDef(
        name = name,
        description = "Play a loud sound on the phone to help locate it, or stop it.",
        parameters = Parameters(
            properties = mapOf(
                "state" to Property("string", "'on' to start ringing, 'off' to stop")
            )
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val state = request.string("state")?.lowercase() ?: "on"

        if (state == "off") {
            stopRinging()
            return ToolResult.Success("Phone stopped ringing.")
        }

        return try {
            startRinging()
            ToolResult.Success("Ringing your phone now.")
        } catch (e: Exception) {
            ToolResult.Failure("Failed to ring phone: ${e.message}", reason = "hardware_error")
        }
    }

    private fun startRinging() {
        synchronized(lock) {
            if (activeRingtone?.isPlaying == true) return

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(context, uri).apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            
            ringtone.play()
            activeRingtone = ringtone

            // Safety auto-stop after 30 seconds
            autoStopJob?.cancel()
            autoStopJob = GlobalScope.launch(Dispatchers.Main) {
                delay(30_000)
                stopRinging()
            }
        }
    }

    private fun stopRinging() {
        synchronized(lock) {
            autoStopJob?.cancel()
            autoStopJob = null
            activeRingtone?.stop()
            activeRingtone = null
        }
    }

    companion object {
        private val lock = Any()
        private var activeRingtone: Ringtone? = null
        private var autoStopJob: Job? = null
    }
}
