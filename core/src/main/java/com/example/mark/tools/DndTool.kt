package com.example.mark.tools

import android.app.NotificationManager
import android.content.Context
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType

class DndTool(private val context: Context) : Tool {

    override val name = "dnd_manager"

    override val intent = IntentType.SET_DND

    override val definition = FunctionDef(
        name = name,
        description = "Turn 'Do Not Disturb' mode on or off.",
        parameters = Parameters(
            properties = mapOf(
                "state" to Property("string", "Either 'on' or 'off'.")
            ),
            required = listOf("state")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return ToolResult.Failure("Notification service not available.", reason = "service_error")

        if (!manager.isNotificationPolicyAccessGranted) {
            // Take the user straight to the switch rather than just refusing.
            val opened = runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            return ToolResult.Failure(
                if (opened) "I need Do Not Disturb access. I've opened the setting, sir: turn on Mark."
                else "Do Not Disturb permission not granted. Please enable it in system settings.",
                reason = "no_permission"
            )
        }

        val state = request.string("state")?.lowercase() ?: "on"
        val filter = if (state == "on") {
            NotificationManager.INTERRUPTION_FILTER_NONE
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }

        val currentFilter = manager.currentInterruptionFilter
        val previousState = if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) "off" else "on"

        return try {
            manager.setInterruptionFilter(filter)
            ToolResult.Success("Do Not Disturb turned $state.", undoParams = mapOf("state" to previousState))
        } catch (e: Exception) {
            ToolResult.Failure("Failed to change DND mode: ${e.message}", reason = "hardware_error")
        }
    }
}
