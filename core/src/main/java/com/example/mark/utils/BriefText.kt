package com.example.mark.utils

/** Composes the morning brief offline, from facts already gathered: no LLM, no cost, no key needed. */
object BriefText {

    data class Event(val title: String, val time: String)

    fun compose(
        hour: Int,
        name: String?,
        weather: String?,
        events: List<Event>,
        reminders: List<String>,
        batteryPercent: Int?,
    ): String {
        val salutation = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        val parts = mutableListOf("$salutation, ${name?.takeIf { it.isNotBlank() } ?: "sir"}.")
        weather?.takeIf { it.isNotBlank() }?.let { parts += it.trim().removeSuffix(".") + "." }
        parts += when (events.size) {
            0 -> "No meetings today."
            1 -> "One meeting: ${events[0].title} at ${events[0].time}."
            else -> "${events.size} meetings: " + events.take(4).joinToString(", ") { "${it.title} at ${it.time}" } +
                (if (events.size > 4) ", and more." else ".")
        }
        if (reminders.isNotEmpty()) {
            parts += (if (reminders.size == 1) "One reminder: " else "${reminders.size} reminders: ") +
                reminders.take(3).joinToString(", ") + "."
        }
        batteryPercent?.takeIf { it < 40 }?.let { parts += "Phone battery is at $it percent." }
        return parts.joinToString(" ")
    }
}
