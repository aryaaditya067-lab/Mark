package com.example.mark.assistant

import com.google.gson.JsonObject

/**
 * Arguments for one tool call, from either path:
 * The LLM sends a JSON string, the offline resolver sends captured regex groups.
 * Tools should not care which.
 */
class ToolRequest private constructor(
    private val params: Map<String, String>,
    val rawInput: String? = null
) {

    fun string(key: String): String? = params[key]?.takeIf { it.isNotBlank() }

    fun int(key: String): Int? = params[key]?.toIntOrNull()

    fun boolean(key: String, default: Boolean): Boolean =
        params[key]?.toBooleanStrictOrNull() ?: default

    val isEmpty: Boolean get() = params.isEmpty()

    companion object {

        fun of(params: Map<String, String> = emptyMap(), rawInput: String? = null) = 
            ToolRequest(params, rawInput)

        /** The LLM serialises arguments as a JSON string, never as an object. */
        fun fromJson(json: JsonObject, rawInput: String? = null): ToolRequest {
            val flat = json.entrySet().associate { (key, value) ->
                key to if (value.isJsonPrimitive) value.asString else value.toString()
            }
            return ToolRequest(flat, rawInput)
        }
    }
}
