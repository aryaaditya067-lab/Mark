package com.example.mark.assistant

import com.example.mark.router.IntentType
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.example.mark.network.Tool as GroqToolSchema

/**
 * Dispatches to tools. Knows nothing about any individual tool — the registry
 * is the only lookup, and there is no `when` on tool name anywhere.
 */
class ToolManager(private val registry: ToolRegistry) {

    private val gson = Gson()

    /** Schemas sent to Groq with every request. */
    val definitions: List<GroqToolSchema> =
        registry.all.map { GroqToolSchema(function = it.definition) }

    fun supports(intent: IntentType): Boolean = registry.supports(intent)

    /** Groq path. [arguments] is the raw JSON string Groq produced. */
    suspend fun execute(name: String, arguments: String, rawInput: String? = null): ToolResult {
        val tool = registry[name]
            ?: return ToolResult.Failure("Unknown tool: $name", reason = "not_registered")

        val json = runCatching {
            gson.fromJson(arguments, JsonObject::class.java)
        }.getOrNull() ?: JsonObject()

        return run(tool, ToolRequest.fromJson(json, rawInput))
    }

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
     * whole turn — Groq can work with "this failed", not with a crash.
     */
    private suspend fun run(tool: Tool, request: ToolRequest): ToolResult =
        runCatching { tool.execute(request) }
            .getOrElse { e ->
                ToolResult.Failure(
                    text = "Failed: ${e.message ?: "tool error"}",
                    reason = "exception"
                )
            }
}
