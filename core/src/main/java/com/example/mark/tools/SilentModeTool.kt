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

/**
 * Ringer mode: silent or normal. Uses AudioManager ringer mode, not DND —
 * "chup ho ja" means stop the ringer, not block every notification.
 */
class SilentModeTool(private val context: Context) : Tool {

    override val name = "silent_manager"

    override val intent = IntentType.SET_SILENT

    override val definition = FunctionDef(
        name = name,
        description = "Silence the ringer or turn sound back on. Provide 'state' as 'on' (silent) or 'off' (sound).",
        parameters = Parameters(
            properties = mapOf(
                "state" to Property("string", "'on' to silence, 'off' to restore sound")
            ),
            required = listOf("state")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult.Failure("Audio service unavailable.", reason = "service_error")

        val previousMode = audio.ringerMode
        val previousState = if (previousMode == AudioManager.RINGER_MODE_NORMAL) "off" else "on"
        val silent = request.string("state")?.lowercase() != "off"

        return try {
            audio.ringerMode = if (silent) AudioManager.RINGER_MODE_SILENT
                               else AudioManager.RINGER_MODE_NORMAL
            ToolResult.Success(
                if (silent) "Silenced." else "Sound is back on.",
                undoParams = mapOf("state" to previousState)
            )
        } catch (e: SecurityException) {
            // Setting silent can require DND access on some OEM builds.
            ToolResult.Failure(
                "I need Do Not Disturb access to silence the phone. Enable it in settings.",
                reason = "no_permission"
            )
        }
    }
}
