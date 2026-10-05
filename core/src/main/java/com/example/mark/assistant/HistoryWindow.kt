package com.example.mark.assistant

import com.example.mark.model.Message
import com.google.gson.JsonParser

/**
 * Picks the slice of past conversation sent to Groq as context.
 *
 * A plain `takeLast(n)` can cut a tool exchange in half: the window may open
 * on a "tool" message whose assistant tool_calls message fell outside it, or
 * end on an assistant tool_calls message whose results were never written
 * (the turn died mid-way). Groq rejects both with a 400, so every request
 * after that fails until the history scrolls past the damage.
 */
internal object HistoryWindow {

    fun select(history: List<Message>, limit: Int): List<Message> {
        if (limit <= 0) return emptyList()
        return wellFormed(history.takeLast(limit))
    }

    /**
     * Drops tool results without a preceding call, and tool calls without
     * every one of their results directly after them. Plain user/assistant
     * messages are always kept.
     */
    fun wellFormed(messages: List<Message>): List<Message> {
        val out = ArrayList<Message>(messages.size)
        var i = 0
        while (i < messages.size) {
            val msg = messages[i]
            when {
                msg.role == "tool" -> i++ // orphan: its call was not kept

                msg.role == "assistant" && msg.toolCallsJson != null -> {
                    val results = messages.drop(i + 1).takeWhile { it.role == "tool" }
                    val expected = toolCallIds(msg.toolCallsJson)
                    val answered = results.mapNotNull { it.toolCallId }.toSet()
                    if (expected.isNotEmpty() && answered.containsAll(expected)) {
                        out.add(msg)
                        out.addAll(results)
                    }
                    i += 1 + results.size
                }

                else -> { out.add(msg); i++ }
            }
        }
        return out
    }

    /** Ids from a serialised List<ToolCall>; empty if it cannot be read. */
    private fun toolCallIds(json: String): Set<String> = runCatching {
        JsonParser.parseString(json).asJsonArray
            .mapNotNull { it.asJsonObject.get("id")?.asString }
            .toSet()
    }.getOrDefault(emptySet())
}
