package com.example.mark.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Time maths for reminders, kept pure so it can be tested. */
object ReminderTimes {

    val REPEATS = setOf("none", "daily", "weekly")

    /**
     * When a reminder should fire, from what the LLM sent: an ISO local
     * date-time ("2026-10-05T18:00"), a bare time ("18:00", today or else
     * tomorrow), or minutes from now. Null when nothing usable was given or
     * the time is already in the past.
     */
    fun resolve(at: String?, inMinutes: Int?, now: ZonedDateTime): ZonedDateTime? {
        if (inMinutes != null) return if (inMinutes > 0) now.plusMinutes(inMinutes.toLong()).withNano(0) else null
        val text = at?.trim()?.replace(' ', 'T')?.takeIf { it.isNotEmpty() } ?: return null
        val local = runCatching { LocalDateTime.parse(text.take(16)) }.getOrNull()
        if (local != null) return local.atZone(now.zone).takeIf { it.isAfter(now) }
        val time = runCatching { LocalTime.parse(text.removePrefix("T").take(5)) }.getOrNull() ?: return null
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** The next firing of a repeating reminder strictly after [now], or null for one-offs. */
    fun next(atMillis: Long, repeat: String, now: ZonedDateTime): Long? {
        val step = when (repeat) { "daily" -> 1L; "weekly" -> 7L; else -> return null }
        var at = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(atMillis), now.zone)
        while (!at.isAfter(now)) at = at.plusDays(step)
        return at.toInstant().toEpochMilli()
    }

    /** "today at 6:00 PM", "tomorrow at 9:30 AM", "on Friday at 7:00 AM", "on 12 March at 8:00 AM". */
    fun describe(atMillis: Long, now: ZonedDateTime): String {
        val at = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(atMillis), now.zone)
        val time = at.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        val today: LocalDate = now.toLocalDate()
        val day = at.toLocalDate()
        return when {
            day == today -> "today at $time"
            day == today.plusDays(1) -> "tomorrow at $time"
            day.isBefore(today.plusDays(7)) -> "on ${day.dayOfWeek.display()} at $time"
            else -> "on ${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} at $time"
        }
    }

    private fun DayOfWeek.display() = getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun now(): ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
}
