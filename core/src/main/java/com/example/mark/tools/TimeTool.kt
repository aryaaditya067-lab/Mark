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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Time, timers, and the stopwatch.
 *
 * Asking the time is answered directly. Timer and stopwatch hand off to the
 * system clock app via AlarmClock intents — Mark does not run a countdown
 * itself; the clock app does, which means it survives Mark being closed.
 */
class TimeTool(private val context: Context) : Tool {

    override val name = "time_manager"

    override val intent = IntentType.TIME_MANAGER

    override val definition = FunctionDef(
        name = name,
        description = "Tell the time, or set a timer / start a stopwatch. " +
                "action: 'time', 'timer', 'stopwatch'. For timer, provide 'seconds'.",
        parameters = Parameters(
            properties = mapOf(
                "action" to Property("string", "'time', 'timer', or 'stopwatch'"),
                "seconds" to Property("string", "Timer length in seconds")
            ),
            required = listOf("action")
        )
    )

    private companion object {
        private var timerEndTime = 0L
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (request.isEmpty && request.rawInput?.contains(Regex("timer|time bacha")) == true) {
            return queryTimer()
        }
        
        return when (request.string("action")?.lowercase()) {
            "timer" -> setTimer(request.int("seconds"))
            "stopwatch" -> startStopwatch()
            "stop" -> stopAll()
            "query" -> queryTimer()
            else -> tellTime()
        }
    }
    
    // Also handle direct IntentType.TIMER_QUERY
    class TimerQuery(private val parent: TimeTool) : Tool {
        override val name = "timer_query"
        override val intent = IntentType.TIMER_QUERY
        override val definition = FunctionDef(name, "Check remaining timer time.", Parameters(properties = emptyMap()))
        override suspend fun execute(request: ToolRequest) = parent.queryTimer()
    }

    private fun queryTimer(): ToolResult {
        val now = System.currentTimeMillis()
        if (timerEndTime <= now) {
            return ToolResult.Success("Koi timer chal nahi raha sir.")
        }
        
        val remaining = (timerEndTime - now) / 1000
        val text = if (remaining >= 60) {
            "${(remaining / 60)} minute bache hain sir."
        } else {
            "$remaining second sir."
        }
        
        return ToolResult.Success(text, data = mapOf("remaining_seconds" to remaining.toString()))
    }

    private fun tellTime(): ToolResult {
        val now = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date())
        return ToolResult.Success("It's $now.", data = mapOf("time" to now))
    }

    private fun stopAll(): ToolResult {
        // Unfortunately, there is no generic system-wide "stop all timers" intent
        // that works across all clock apps without specific package names.
        // However, we can try to dismiss an alarm if one is firing.
        return try {
            val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Attempting to stop active alerts.")
        } catch (e: Exception) {
            ToolResult.Failure("I can't stop the clock app from here.", reason = "no_handler")
        }
    }

    private fun setTimer(seconds: Int?): ToolResult {
        if (seconds == null || seconds <= 0) {
            return ToolResult.Failure("How long should the timer be?", reason = "missing_arg")
        }
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)   // set it silently, no clock UI
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            timerEndTime = System.currentTimeMillis() + (seconds * 1000L)
            val label = if (seconds >= 60) "${seconds / 60} minute" else "$seconds second"
            ToolResult.Success("$label timer set.")
        } catch (e: Exception) {
            ToolResult.Failure("No clock app could set the timer.", reason = "no_handler")
        }
    }

    private fun startStopwatch(): ToolResult {
        return try {
            val intent = Intent("android.intent.action.START_STOPWATCH").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Stopwatch started.")
        } catch (e: Exception) {
            ToolResult.Failure("No clock app could start the stopwatch.", reason = "no_handler")
        }
    }
}
