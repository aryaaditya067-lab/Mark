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

    /** Also accepts "20.0": JSON numbers from the LLM are not always integers. */
    fun int(key: String): Int? = params[key]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

    fun boolean(key: String, default: Boolean): Boolean =
        params[key]?.toBooleanStrictOrNull() ?: default

    val isEmpty: Boolean get() = params.isEmpty()

    companion object {

        fun of(params: Map<String, String> = emptyMap(), rawInput: String? = null) = 
            ToolRequest(params, rawInput)

        /** The LLM serialises arguments as a JSON string, never as an object. */
        fun fromJson(json: JsonObject, rawInput: String? = null): ToolRequest =
            ToolRequest(flatten(json), rawInput)

        /** Same flattening, as the plain map the offline path and transport use. */
        fun flatten(json: JsonObject): Map<String, String> =
            json.entrySet().associate { (key, value) ->
                key to if (value.isJsonPrimitive) value.asString else value.toString()
            }
    }
}
