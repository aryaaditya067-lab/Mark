package com.example.mark.tools

import android.content.Context
import android.media.AudioManager
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType

class VolumeTool(private val context: Context) : Tool {

    override val name = "volume_manager"

    override val intent = IntentType.SET_VOLUME

    override val definition = FunctionDef(
        name = name,
        description = "Adjust or check the device volume.",
        parameters = Parameters(
            properties = mapOf(
                "action" to Property("string", "Action to perform: 'set', 'mute', 'unmute', 'get'"),
                "level" to Property("string", "Percentage level (0-100) for 'set' action")
            ),
            required = listOf("action")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult.Failure("Audio service not available.", reason = "service_error")

        val action = request.string("action")
        val level = request.int("level")
        val direction = request.string("direction")
        
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentPct = (current * 100 / max.toFloat()).toInt()

        return when {
            action == "mute" -> {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                ToolResult.Success("Volume muted.", undoParams = mapOf("level" to currentPct.toString()))
            }
            action == "unmute" -> {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, max / 2, 0)
                ToolResult.Success("Volume unmuted.", undoParams = mapOf("level" to currentPct.toString()))
            }
            level != null -> {
                val target = (max * (level.coerceIn(0, 100) / 100f)).toInt()
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                ToolResult.Success("Volume set to $level%.", undoParams = mapOf("level" to currentPct.toString()))
            }
            direction == "up" -> {
                val targetPct = (currentPct + 15).coerceIn(0, 100)
                val target = (max * (targetPct / 100f)).toInt()
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                ToolResult.Success("Volume increased to $targetPct%.", undoParams = mapOf("level" to currentPct.toString()))
            }
            direction == "down" -> {
                val targetPct = (currentPct - 15).coerceIn(0, 100)
                val target = (max * (targetPct / 100f)).toInt()
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                ToolResult.Success("Volume decreased to $targetPct%.", undoParams = mapOf("level" to currentPct.toString()))
            }
            else -> {
                ToolResult.Success("Volume is at $currentPct%.", data = mapOf("percentage" to currentPct.toString()))
            }
        }
    }
}
