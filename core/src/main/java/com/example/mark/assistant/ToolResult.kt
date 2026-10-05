package com.example.mark.assistant

/**
 * What a tool hands back.
 *
 * [text] is what the LLM reads as the tool result, and what the offline response
 * templates start from — so it must always read as plain, factual English.
 * Tools never throw; a failure is a value, not an exception, because the LLM
 * needs to be told what went wrong in order to explain it.
 */
sealed interface ToolResult {

    val text: String

    /** Structured values, for offline templates that want the raw number. */
    val data: Map<String, String>

    data class Success(
        override val text: String,
        override val data: Map<String, String> = emptyMap(),
        val pendingIntent: com.example.mark.router.Intent? = null,
        val undoParams: Map<String, String>? = null
    ) : ToolResult

    /** Worked, but the answer is incomplete or stale. */
    data class Partial(
        override val text: String,
        val reason: String,
        override val data: Map<String, String> = emptyMap()
    ) : ToolResult

    data class Failure(
        override val text: String,
        val reason: String
    ) : ToolResult {
        override val data: Map<String, String> get() = emptyMap()
    }
}
