package com.example.mark.tools

import com.example.mark.assistant.PhoneToolSchemas
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.model.Reminder
import com.example.mark.repository.Reminders
import com.example.mark.router.IntentType
import com.example.mark.utils.ReminderTimes
import java.time.ZonedDateTime

/**
 * Reminders that actually fire. On the phone [store] schedules alarms; on the
 * watch these are forwarded to the phone by intent, so [store] is unused there.
 */
class SetReminderTool(private val store: Reminders, private val clock: () -> ZonedDateTime = ReminderTimes::now) : Tool {

    override val name = "set_reminder"
    override val intent = IntentType.SET_REMINDER

    override val definition = PhoneToolSchemas.SET_REMINDER

    override suspend fun execute(request: ToolRequest): ToolResult {
        val text = request.string("text") ?: return ToolResult.Failure("What should I remind you about?", reason = "missing_arg")
        val now = clock()
        val at = ReminderTimes.resolve(request.string("at"), request.int("in_minutes"), now)
            ?: return ToolResult.Failure("When should I remind you, sir?", reason = "missing_arg")
        val repeat = request.string("repeat")?.lowercase()?.takeIf { it in ReminderTimes.REPEATS } ?: "none"
        val reminder = Reminder(text = text.trim(), at = at.toInstant().toEpochMilli(), repeat = repeat)
        store.add(reminder)
        val every = when (repeat) { "daily" -> ", every day"; "weekly" -> ", every week"; else -> "" }
        return ToolResult.Success(
            "Reminder set for ${ReminderTimes.describe(reminder.at, now)}$every: ${reminder.text}",
            mapOf("id" to reminder.id)
        )
    }
}

class ListRemindersTool(private val store: Reminders, private val clock: () -> ZonedDateTime = ReminderTimes::now) : Tool {

    override val name = "list_reminders"
    override val intent = IntentType.LIST_REMINDERS

    override val definition = PhoneToolSchemas.LIST_REMINDERS

    override suspend fun execute(request: ToolRequest): ToolResult {
        val now = clock()
        val upcoming = store.list().sortedBy { it.at }
        if (upcoming.isEmpty()) return ToolResult.Success("No reminders set.")
        return ToolResult.Success(upcoming.joinToString("; ") { "${it.text}, ${ReminderTimes.describe(it.at, now)}" })
    }
}

class CancelReminderTool(private val store: Reminders) : Tool {

    override val name = "cancel_reminder"
    override val intent = IntentType.CANCEL_REMINDER

    override val definition = PhoneToolSchemas.CANCEL_REMINDER

    override suspend fun execute(request: ToolRequest): ToolResult {
        val words = request.string("query")?.lowercase()?.split(Regex("\\s+"))?.filter { it.length > 2 }
        if (words.isNullOrEmpty()) return ToolResult.Failure("Which reminder should I cancel?", reason = "missing_arg")
        val matches = store.list().filter { r -> words.all { it in r.text.lowercase() } }
        if (matches.isEmpty()) return ToolResult.Failure("No reminder matches that.", reason = "not_found")
        store.cancel(matches.map { it.id }.toSet())
        return ToolResult.Success("Cancelled: " + matches.joinToString("; ") { it.text })
    }
}
