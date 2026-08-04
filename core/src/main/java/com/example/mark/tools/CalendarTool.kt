package com.example.mark.tools

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType
import java.text.SimpleDateFormat
import java.util.*

class CalendarTool(private val context: Context) : Tool {

    override val name = "calendar_manager"

    override val intent = IntentType.GET_CALENDAR

    override val definition = FunctionDef(
        name = name,
        description = "Manage user's calendar. Can create events, list events for a specific date, or search events.",
        parameters = Parameters(
            properties = mapOf(
                "action" to Property("string", "The action to perform: 'create', 'list', or 'search'"),
                "title" to Property("string", "Event title for create or search"),
                "date" to Property("string", "Date for list or create (YYYY-MM-DD). Use 'today' or 'tomorrow' as shortcuts."),
                "time" to Property("string", "Time for create (HH:mm)"),
                "duration_minutes" to Property("string", "Duration for create (default 60)")
            ),
            required = listOf("action")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val readGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

        val action = request.string("action") ?: "list"

        if (action == "create" && !writeGranted) {
            return ToolResult.Failure("Calendar write permission not granted.", reason = "no_permission")
        }
        if ((action == "list" || action == "search") && !readGranted) {
            return ToolResult.Failure("Calendar read permission not granted.", reason = "no_permission")
        }

        return when (action) {
            "create" -> createEvent(request)
            "search" -> searchEvents(request.string("title") ?: "")
            else -> listEvents(request.string("date") ?: "today")
        }
    }

    private fun listEvents(dateStr: String): ToolResult {
        val calendar = parseDate(dateStr)
        val startMillis = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val endMillis = calendar.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis

        val events = queryEvents(startMillis, endMillis)
        if (events.isEmpty()) {
            return ToolResult.Success("No events found for ${formatDate(startMillis)}.")
        }

        val text = "Events for ${formatDate(startMillis)}:\n" + events.joinToString("\n") { "- ${it.title} at ${it.time}" }
        return ToolResult.Success(text, data = mapOf("count" to events.size.toString()))
    }

    private fun searchEvents(query: String): ToolResult {
        if (query.isBlank()) return ToolResult.Failure("Missing search query.", reason = "missing_arg")
        
        val now = System.currentTimeMillis()
        val future = now + (30L * 24 * 60 * 60 * 1000) // Search 30 days ahead
        
        val events = queryEvents(now, future, query)
        if (events.isEmpty()) {
            return ToolResult.Success("No upcoming events found matching '$query'.")
        }

        val text = "Found events matching '$query':\n" + events.joinToString("\n") { "- ${it.title} on ${it.date} at ${it.time}" }
        return ToolResult.Success(text, data = mapOf("count" to events.size.toString()))
    }

    private fun createEvent(request: ToolRequest): ToolResult {
        val title = request.string("title") ?: return ToolResult.Failure("Missing event title", reason = "missing_arg")
        val dateStr = request.string("date") ?: "today"
        val timeStr = request.string("time") ?: return ToolResult.Failure("Missing event time", reason = "missing_arg")
        
        val calendar = parseDate(dateStr)
        val timeParts = timeStr.split(":")
        if (timeParts.size < 2) return ToolResult.Failure("Invalid time format. Use HH:mm", reason = "invalid_arg")
        
        calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
        calendar.set(Calendar.MINUTE, timeParts[1].toInt())
        
        val startMillis = calendar.timeInMillis
        val duration = request.string("duration_minutes")?.toIntOrNull() ?: 60
        val endMillis = startMillis + (duration * 60 * 1000)

        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.CALENDAR_ID, 1) // Default calendar
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ToolResult.Success("Event '$title' created for ${formatDateTime(startMillis)}.")
        } catch (e: Exception) {
            ToolResult.Failure("Failed to create event: ${e.message}", reason = "provider_error")
        }
    }

    private fun queryEvents(start: Long, end: Long, titleQuery: String? = null): List<EventInfo> {
        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART
        )
        
        var selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?)"
        val selectionArgs = mutableListOf(start.toString(), end.toString())
        
        if (titleQuery != null) {
            selection += " AND (${CalendarContract.Events.TITLE} LIKE ?)"
            selectionArgs.add("%$titleQuery%")
        }

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs.toTypedArray(),
            "${CalendarContract.Events.DTSTART} ASC"
        )

        val result = mutableListOf<EventInfo>()
        cursor?.use {
            val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
            while (it.moveToNext()) {
                val time = it.getLong(startIdx)
                result.add(EventInfo(
                    title = it.getString(titleIdx),
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time)),
                    time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
                ))
            }
        }
        return result
    }

    private fun parseDate(str: String): Calendar {
        val cal = Calendar.getInstance()
        when (str.lowercase()) {
            "today" -> {}
            "tomorrow" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            else -> {
                runCatching {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(str)
                    if (date != null) cal.time = date
                }
            }
        }
        return cal
    }

    private fun formatDate(millis: Long) = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(millis))
    private fun formatDateTime(millis: Long) = SimpleDateFormat("EEEE, d MMMM 'at' HH:mm", Locale.getDefault()).format(Date(millis))

    private data class EventInfo(val title: String, val date: String, val time: String)
}
