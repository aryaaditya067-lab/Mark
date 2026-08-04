package com.example.mark.tools

import android.content.Context
import com.example.mark.assistant.HealthProvider
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.repository.DailyStatsRepository
import com.example.mark.router.IntentType

class StepsTool(
    private val context: Context,
    private val health: HealthProvider
) : Tool {

    override val name = "get_steps"

    override val intent = IntentType.GET_STEPS

    override val definition = FunctionDef(
        name = name,
        description = "How many steps the user walked today.",
        parameters = Parameters(properties = emptyMap())
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (!health.isAvailable()) {
            return ToolResult.Failure(
                "No health data available.",
                reason = "no_permission"
            )
        }

        val steps = health.todaySteps()
            ?: return ToolResult.Failure("No step data recorded today.", reason = "no_data")

        val statsRepo = DailyStatsRepository.getInstance(context)
        val yesterdaySteps = statsRepo.getYesterdaySteps()
        statsRepo.saveSteps(steps)

        val comparison = if (yesterdaySteps > 0) {
            val diff = (steps - yesterdaySteps).toFloat() / yesterdaySteps
            when {
                kotlin.math.abs(diff) <= 0.10f -> "same as yesterday"
                diff > 0 -> "more than yesterday"
                else -> "less than yesterday"
            }
        } else null

        val age = health.capturedAt()?.let { minutesSince(it) } ?: 0
        val data = mutableMapOf("steps" to steps.toString())
        comparison?.let { data["comparison"] = it }

        return if (age > 30) {
            ToolResult.Partial(
                text = "Steps today: $steps (as of $age minutes ago).",
                reason = "stale",
                data = data
            )
        } else {
            ToolResult.Success(
                text = "Steps today: $steps.",
                data = data
            )
        }
    }

    private fun minutesSince(millis: Long) =
        ((System.currentTimeMillis() - millis) / 60_000).toInt()
}
