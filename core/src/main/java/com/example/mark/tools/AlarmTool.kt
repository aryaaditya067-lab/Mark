package com.example.mark.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType

/**
 * Sets, dismisses, or snoozes alarms on the system clock.
 */
class AlarmTool(private val context: Context) : Tool {

    override val name = "set_alarm"
    override val intent = IntentType.SET_ALARM

    override val definition = FunctionDef(
        name = name,
        description = "Set, dismiss, or snooze an alarm. For setting, time must be HH:mm.",
        parameters = Parameters(
            properties = mapOf(
                "time" to Property("string", "Time in 24h format (HH:mm)"),
                "state" to Property("string", "'on' to set, 'off' to dismiss, 'snooze' to snooze"),
                "target" to Property("string", "Target device: 'phone' or 'watch'")
            )
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val state = request.string("state")?.lowercase() ?: "on"

        return when (state) {
            "off" -> dismissAlarm()
            "snooze" -> snoozeAlarm()
            else -> {
                // "10 minute baad alarm": the router extracts relative_minutes, which
                // used to be ignored, so the alarm was never set.
                val time = request.string("time")
                    ?: request.int("relative_minutes")?.takeIf { it > 0 }?.let {
                        java.time.LocalTime.now().plusMinutes(it.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    }
                    ?: return ToolResult.Failure("What time should I set the alarm for?", reason = "missing_arg")
                setAlarm(time)
            }
        }
    }

    private fun setAlarm(time: String): ToolResult {
        val parts = time.split(":")
        if (parts.size != 2) return ToolResult.Failure("Invalid time format.", reason = "invalid_arg")
        
        val hour = parts[0].toIntOrNull() ?: return ToolResult.Failure("Invalid hour.", reason = "invalid_arg")
        val minute = parts[1].toIntOrNull() ?: 0

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Alarm set for $time.")
        } catch (e: Exception) {
            ToolResult.Failure("Could not set alarm: ${e.message}", reason = "system_error")
        }
    }

    private fun dismissAlarm(): ToolResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Alarm dismissed.")
        } catch (e: Exception) {
            // Dismiss alarm might not be supported by all clock apps via intent
            ToolResult.Failure("Could not dismiss alarm automatically.", reason = "system_error")
        }
    }

    private fun snoozeAlarm(): ToolResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SNOOZE_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Alarm snoozed.")
        } catch (e: Exception) {
            ToolResult.Failure("Could not snooze alarm.", reason = "system_error")
        }
    }
}
