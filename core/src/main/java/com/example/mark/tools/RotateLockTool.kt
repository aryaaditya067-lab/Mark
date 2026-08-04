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
 * Auto-rotate on/off. Same WRITE_SETTINGS grant as brightness.
 */
class RotateLockTool(private val context: Context) : Tool {

    override val name = "rotate_manager"

    override val intent = IntentType.SET_ROTATE

    override val definition = FunctionDef(
        name = name,
        description = "Lock or unlock screen auto-rotation. 'state' is 'off' to lock, 'on' to allow rotation.",
        parameters = Parameters(
            properties = mapOf(
                "state" to Property("string", "'on' allows rotation, 'off' locks it")
            ),
            required = listOf("state")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (!Settings.System.canWrite(context)) {
            val isWatch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

            if (isWatch) {
                return ToolResult.Failure(
                    "Mark needs 'Modify System Settings' permission on your watch to change rotation. Please enable it in the watch settings.",
                    reason = "no_permission"
                )
            }

            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return ToolResult.Failure(
                "I need permission for that. I've opened the settings screen — enable it and try again.",
                reason = "no_permission"
            )
        }

        val current = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION)
        }.getOrDefault(1)
        val previousState = if (current == 0) "on" else "off"

        // "lock" -> state=on -> locked. "unlock" -> state=off -> rotating allowed.
        val locked = request.string("state")?.lowercase() == "on"

        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (locked) 0 else 1
            )
            ToolResult.Success(
                if (locked) "Rotation locked." else "Rotation unlocked.",
                undoParams = mapOf("state" to previousState)
            )
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't change rotation: ${e.message}", reason = "system_error")
        }
    }
}
