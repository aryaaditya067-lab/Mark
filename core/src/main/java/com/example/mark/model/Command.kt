package com.example.mark.model

import com.example.mark.router.IntentType

/**
 * Encapsulates a command sent between devices (e.g. Watch -> Phone).
 */
data class Command(
    val id: String,
    val type: IntentType,
    val params: Map<String, String> = emptyMap()
)

/**
 * Result of a command execution, returned to the sender.
 */
data class CommandResult(
    val commandId: String,
    val text: String,
    val success: Boolean,
    val data: Map<String, String> = emptyMap()
)
