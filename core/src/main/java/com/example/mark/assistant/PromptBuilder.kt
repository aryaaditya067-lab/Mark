package com.example.mark.assistant

import com.example.mark.utils.Constants
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the system message. Time is injected on every request so the model can
 * resolve relative expressions like "tomorrow" or "in 20 minutes".
 */
object PromptBuilder {

    private val formatter =
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH)

    fun systemPrompt(): String {
        val now = LocalDateTime.now().format(formatter)
        return buildString {
            append(Constants.SYSTEM_PROMPT)
            append("\n\nCurrent date and time: ").append(now).append(" (24-hour clock).")
            append("\nUse this to resolve relative times such as 'tomorrow', ")
            append("'in 20 minutes', '4 in the morning' (04:00) or ")
            append("'4 in the evening' (16:00).")
        }
    }
}