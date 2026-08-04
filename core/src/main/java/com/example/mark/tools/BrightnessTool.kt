package com.example.mark.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType

/**
 * Screen brightness. Absolute ("brightness 50") or relative ("brighter").
 *
 * Writing brightness needs WRITE_SETTINGS, which is not a runtime permission —
 * the user grants it once on a system screen. When it is missing the tool does
 * not throw; it returns a Failure telling the user (or Groq) how to grant it.
 */
class BrightnessTool(private val context: Context) : Tool {

    override val name = "brightness_manager"

    override val intent = IntentType.SET_BRIGHTNESS

    override val definition = FunctionDef(
        name = name,
        description = "Set screen brightness. Provide 'level' 0-100, or 'direction' up/down.",
        parameters = Parameters(
            properties = mapOf(
                "level" to Property("string", "Absolute brightness percentage 0-100"),
                "direction" to Property("string", "'up' or 'down' for relative change")
            )
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (!Settings.System.canWrite(context)) {
            val isWatch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
            
            if (isWatch) {
                return ToolResult.Failure(
                    "Mark needs 'Modify System Settings' permission on your watch to change brightness. Please enable it in the watch settings.",
                    reason = "no_permission"
                )
            }

            // Phone path: Send the user straight to the grant screen.
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return ToolResult.Failure(
                "I need permission to change brightness. I've opened the settings screen — enable it and try again.",
                reason = "no_permission"
            )
        }

        val max = 255
        val current = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)

        val currentPct = current * 100 / max
        val targetPct = when {
            request.int("level") != null -> request.int("level")!!.coerceIn(0, 100)
            request.string("direction") == "up" -> (currentPct + 25).coerceIn(0, 100)
            request.string("direction") == "down" -> (currentPct - 25).coerceIn(0, 100)
            else -> return ToolResult.Failure("Tell me a brightness level or direction.", reason = "missing_arg")
        }

        return try {
            // ... (rest of the logic)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                (targetPct * max / 100)
            )
            ToolResult.Success(
                "Brightness set to $targetPct%.", 
                data = mapOf("level" to targetPct.toString()),
                undoParams = mapOf("level" to currentPct.toString())
            )
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't change brightness: ${e.message}", reason = "system_error")
        }
    }
}
