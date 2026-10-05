package com.example.mark.assistant

import com.example.mark.router.Intent
import com.example.mark.router.IntentType
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.example.mark.network.Tool as LlmToolSchema

/**
 * Dispatches to tools. Knows nothing about any individual tool — the registry
 * is the only lookup, and there is no `when` on tool name anywhere.
 */
class ToolManager(private val registry: ToolRegistry) {

    private val gson = Gson()

    /** Schemas sent to the LLM with every request. */
    val definitions: List<LlmToolSchema> =
        registry.all.map { LlmToolSchema(function = it.definition) }

    fun supports(intent: IntentType): Boolean = registry.supports(intent)

    /** LLM path. [arguments] is the raw JSON string the model produced. */
    suspend fun execute(name: String, arguments: String, rawInput: String? = null): ToolResult {
        val tool = registry[name]
            ?: return ToolResult.Failure("Unknown tool: $name", reason = "not_registered")
        return run(tool, ToolRequest.fromJson(parse(arguments), rawInput))
    }

    /** The offline intent behind an LLM tool name, if it has one. */
    fun intentOf(name: String): IntentType? = registry[name]?.intent

    /** LLM arguments as the flat map the offline path and transport carry. */
    fun paramsOf(arguments: String): Map<String, String> = ToolRequest.flatten(parse(arguments))

    /**
     * The intent to hold for a spoken "yes" when an LLM call needs approval,
     * or null when it may run straight away.
     */
    fun confirmationFor(name: String, arguments: String): Intent? {
        val tool = registry[name] ?: return null
        val intent = tool.intent ?: return null
        val params = paramsOf(arguments)
        return if (tool.needsConfirmation(ToolRequest.of(params))) Intent(intent, params) else null
    }

    private fun parse(arguments: String): JsonObject =
        runCatching { gson.fromJson(arguments, JsonObject::class.java) }.getOrNull() ?: JsonObject()

    /** Offline path. Params come from the intent resolver's capture groups. */
    suspend fun execute(intent: IntentType, params: Map<String, String>, rawInput: String? = null): ToolResult {
        val tool = registry[intent]
            ?: return ToolResult.Failure(
                "No tool for $intent on this device",
                reason = "not_registered"
            )
        return run(tool, ToolRequest.of(params, rawInput))
    }

    /**
     * Tools are contracted not to throw, but a bug in one must not kill the
     * whole turn — the LLM can work with "this failed", not with a crash.
     * Cancellation is the exception: it is how stop() and barge-in end a turn,
     * so it must propagate rather than become a "failed" reply.
     */
    private suspend fun run(tool: Tool, request: ToolRequest): ToolResult =
        try {
            tool.execute(request)
        } catch (e: Exception) {
            // Rethrows only if this turn was cancelled; a tool's own internal
            // timeout is still just a failed tool.
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            ToolResult.Failure(
                text = "Failed: ${e.message ?: "tool error"}",
                reason = "exception"
            )
        }
}
