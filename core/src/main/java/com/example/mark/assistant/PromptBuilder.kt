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

    /**
     * @param isWatch which device this conversation is happening on; it decides
     *   what "here" means and which actions travel to the phone.
     */
    fun systemPrompt(
        isWatch: Boolean = false,
        facts: List<String> = emptyList(),
        situation: List<String> = emptyList(),
        now: LocalDateTime = LocalDateTime.now()
    ): String =
        buildString {
            append(Constants.SYSTEM_PROMPT)
            append("\n\n")
            append(
                if (isWatch) "You are running on the user's Wear OS watch. Phone actions, calls, messages " +
                    "and the laptop are carried out through their paired phone."
                else "You are running on the user's Android phone, which is paired with their Wear OS " +
                    "watch and can control their Windows laptop over the home network."
            )
            append("\nCurrent date and time: ").append(now.format(formatter)).append(" (24-hour clock).")
            append("\nUse this to resolve relative times such as 'tomorrow', ")
            append("'in 20 minutes', '4 in the morning' (04:00) or ")
            append("'4 in the evening' (16:00).")
            if (situation.isNotEmpty()) {
                append("\n\nRight now (mention only when relevant):")
                situation.forEach { append("\n- ").append(it) }
            }
            if (facts.isNotEmpty()) {
                append("\n\nWhat you know about the user from earlier conversations ")
                append("(use it naturally; don't recite it unless asked):")
                facts.forEach { append("\n- ").append(it) }
            }
        }
}
