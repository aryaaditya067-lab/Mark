package com.example.mark.model

import java.util.UUID

/**
 * One entry in the conversation. Mirrors the LLM message shape so it can be
 * sent back to the API as-is, while also driving the chat UI.
 *
 * role "tool" messages are never shown in the UI.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,                       // "user" | "assistant" | "tool"
    val content: String? = null,
    val toolCallsJson: String? = null,      // serialized List<ToolCall>
    val toolCallId: String? = null,
    val name: String? = null,
    val isToolReply: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isUser: Boolean get() = role == "user"

    /** Only user and assistant messages with text are rendered as bubbles. */
    val isVisible: Boolean
        get() = (role == "user" || role == "assistant") && !content.isNullOrBlank()
}
