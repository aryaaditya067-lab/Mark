package com.example.mark.assistant

import com.example.mark.network.FunctionDef
import com.example.mark.router.IntentType

/**
 * One capability. Reachable two ways:
 *
 *  - LLM function calling, keyed by [name]
 *  - the offline intent router, keyed by [intent]
 *
 * A tool that needs parameters the router cannot extract — a time, a device,
 * a free-text title — leaves [intent] null and is only ever reached through
 * the LLM. That is not a gap; it is where the line honestly falls.
 */
interface Tool {

    /** Unique. This is the name the LLM calls. */
    val name: String

    /** Schema sent to the LLM so it knows when and how to call this tool. */
    val definition: FunctionDef

    /** Non-null when this tool can be reached without an LLM round trip. */
    val intent: IntentType? get() = null

    suspend fun execute(request: ToolRequest): ToolResult

    /**
     * True when this call has consequences the user must approve out loud
     * (dialling, sending a message, shutting a laptop down). The LLM path then
     * asks instead of acting; a spoken "yes" runs it through the offline path,
     * so a tool that returns true must also set [intent].
     */
    fun needsConfirmation(request: ToolRequest): Boolean = false
}
