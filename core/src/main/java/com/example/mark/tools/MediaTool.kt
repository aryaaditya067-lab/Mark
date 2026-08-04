package com.example.mark.tools

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType

/**
 * Media playback control.
 *
 * This does not talk to any specific player. It dispatches the same media key
 * events a headset button sends, and whichever app currently holds the media
 * session responds — Spotify, YouTube Music, whatever last played.
 */
class MediaTool(private val context: Context) : Tool {

    override val name = "media_control"

    override val intent = IntentType.MEDIA_CONTROL

    override val definition = FunctionDef(
        name = name,
        description = "Control music/video playback. action: 'play_pause', 'next', 'previous', 'pause', 'play'.",
        parameters = Parameters(
            properties = mapOf(
                "action" to Property("string", "One of: play_pause, next, previous, pause, play")
            ),
            required = listOf("action")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult.Failure("Audio service unavailable.", reason = "service_error")

        val action = request.string("action")?.lowercase()
            ?: return ToolResult.Failure("What should I do with playback?", reason = "missing_arg")

        val keyCode = when (action) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        return try {
            // A key press is a down followed by an up. Players ignore a lone down.
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))

            val reply = when (action) {
                "next" -> "Skipped ahead."
                "previous", "prev" -> "Went back."
                "pause" -> "Paused."
                "play" -> "Playing."
                else -> "Toggled playback."
            }
            ToolResult.Success(reply)
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't control playback: ${e.message}", reason = "system_error")
        }
    }
}
