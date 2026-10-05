package com.example.mark.network

import com.google.gson.annotations.SerializedName

/** OpenAI-compatible chat request/response DTOs (Xiaomi MiMo). */

data class LlmMessage(
    val role: String,                       // "system" | "user" | "assistant" | "tool"
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null                // tool name, only for role="tool"
)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double = 0.7,
    @SerializedName("max_completion_tokens") val maxTokens: Int = 400,
    val tools: List<Tool>? = null,
    @SerializedName("tool_choice") val toolChoice: String? = null,
    val stream: Boolean = false,
    // MiMo reasons before answering unless told not to. For a voice assistant
    // that is seconds of silence, so it is off by default.
    val thinking: Thinking? = Thinking.DISABLED
)

data class Thinking(val type: String) {
    companion object {
        val DISABLED = Thinking("disabled")
    }
}

data class LlmResponse(
    val id: String?,
    val model: String?,
    val choices: List<LlmChoice>?
)

data class LlmChoice(
    val index: Int?,
    val message: LlmMessage?,
    @SerializedName("finish_reason") val finishReason: String?
)

// --- Tool calling ---

data class ToolCall(
    val id: String,
    val type: String,                       // always "function"
    val function: FunctionCall
)

data class FunctionCall(
    val name: String,
    val arguments: String                   // JSON string, must be parsed
)

data class Tool(
    val type: String = "function",
    val function: FunctionDef
)

data class FunctionDef(
    val name: String,
    val description: String,
    val parameters: Parameters
)

data class Parameters(
    val type: String = "object",
    val properties: Map<String, Property>,
    val required: List<String> = emptyList()
)

data class Property(
    val type: String,
    val description: String
)

// --- Streaming ---

data class LlmStreamResponse(
    val choices: List<LlmStreamChoice>
)

data class LlmStreamChoice(
    val delta: LlmStreamDelta,
    @SerializedName("finish_reason") val finishReason: String?
)

data class LlmStreamDelta(
    val role: String?,
    val content: String?,
    @SerializedName("tool_calls") val toolCalls: List<ToolCallDelta>?
)

data class ToolCallDelta(
    val index: Int,
    val id: String?,
    val function: FunctionCallDelta?
)

data class FunctionCallDelta(
    val name: String?,
    val arguments: String?
)
