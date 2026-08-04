package com.example.mark.tools

import com.example.mark.assistant.HealthProvider
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.router.IntentType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SleepTool(private val health: HealthProvider) : Tool {

    private val clock = SimpleDateFormat("HH:mm", Locale.ENGLISH)

    override val name = "get_sleep"

    override val intent = IntentType.GET_SLEEP

    override val definition = FunctionDef(
        name = name,
        description = "Get how long the user slept last night, and when. Use this " +
                "when they ask about sleep, rest, or how they slept.",
        parameters = Parameters(properties = emptyMap())
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (!health.isAvailable()) {
            return ToolResult.Failure(
                "Health data is not available.",
                reason = "no_permission"
            )
        }

        val sleep = health.lastNightSleep()
            ?: return ToolResult.Failure("No sleep recorded last night.", reason = "no_data")

        val minutes = sleep.sleepMinutes ?: return ToolResult.Failure("No sleep recorded last night.", reason = "no_data")
        val hours = minutes / 60
        val mins = minutes % 60

        val window = if (sleep.sleepStart != null && sleep.sleepEnd != null) {
            " (${clock.format(Date(sleep.sleepStart))} to ${clock.format(Date(sleep.sleepEnd))})"
        } else ""

        val text = "Slept ${hours}h ${mins}m last night$window."
        return ToolResult.Success(
            text = text,
            data = mapOf(
                "hours" to hours.toString(),
                "minutes" to mins.toString(),
                "total_minutes" to minutes.toString()
            )
        )
    }
}
