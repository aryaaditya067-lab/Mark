package com.example.mark.assistant

import com.example.mark.network.FunctionCall
import com.example.mark.network.ToolCall
import com.example.mark.network.ToolCallDelta

/**
 * Rebuilds complete tool calls from a streamed response.
 *
 * A streamed tool call arrives in pieces keyed by `index`: the first piece
 * carries the id and function name, later ones append fragments of the JSON
 * arguments. Assembling them here means the reply never has to be fetched a
 * second time without streaming just to read the tool calls.
 */
internal class ToolCallAccumulator {

    private class Partial {
        var id: String? = null
        var name: String = ""
        val arguments = StringBuilder()
    }

    private val partials = sortedMapOf<Int, Partial>()

    fun add(deltas: List<ToolCallDelta>?) {
        deltas?.forEach { delta ->
            val partial = partials.getOrPut(delta.index ?: 0) { Partial() }
            delta.id?.takeIf { it.isNotBlank() }?.let { partial.id = it }
            // The name arrives whole in one piece; some servers repeat it later.
            delta.function?.name?.takeIf { it.isNotBlank() && partial.name.isEmpty() }
                ?.let { partial.name = it }
            delta.function?.arguments?.let { partial.arguments.append(it) }
        }
    }

    val isEmpty: Boolean get() = build().isEmpty()

    fun build(): List<ToolCall> = partials.mapNotNull { (index, p) ->
        if (p.name.isBlank()) return@mapNotNull null
        ToolCall(
            id = p.id ?: "call_$index",
            type = "function",
            function = FunctionCall(
                name = p.name,
                arguments = p.arguments.toString().ifBlank { "{}" }
            )
        )
    }
}
