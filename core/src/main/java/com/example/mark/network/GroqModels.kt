package com.example.mark.network

import com.google.gson.annotations.SerializedName

/** Groq (OpenAI-compatible) request/response DTOs. */

data class GroqMessage(
    val role: String,                       // "system" | "user" | "assistant" | "tool"
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null                // tool name, only for role="tool"
)

data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 400,
    val tools: List<Tool>? = null,
    @SerializedName("tool_choice") val toolChoice: String? = null,
    val stream: Boolean = false
)

data class GroqResponse(
    val id: String?,
    val model: String?,
    val choices: List<GroqChoice>?
)

data class GroqChoice(
    val index: Int?,
    val message: GroqMessage?,
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

data class GroqStreamResponse(
    val choices: List<GroqStreamChoice>
)

data class GroqStreamChoice(
    val delta: GroqStreamDelta,
    @SerializedName("finish_reason") val finishReason: String?
)

data class GroqStreamDelta(
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
