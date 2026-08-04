package com.example.mark.assistant

import com.example.mark.network.FunctionDef
import com.example.mark.router.IntentType

/**
 * One capability. Reachable two ways:
 *
 *  - Groq function calling, keyed by [name]
 *  - the offline intent router, keyed by [intent]
 *
 * A tool that needs parameters the router cannot extract — a time, a device,
 * a free-text title — leaves [intent] null and is only ever reached through
 * Groq. That is not a gap; it is where the line honestly falls.
 */
interface Tool {

    /** Unique. This is the name Groq calls. */
    val name: String

    /** Schema sent to Groq so it knows when and how to call this tool. */
    val definition: FunctionDef

    /** Non-null when this tool can be reached without an LLM round trip. */
    val intent: IntentType? get() = null

    suspend fun execute(request: ToolRequest): ToolResult
}
