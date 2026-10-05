package com.example.mark.model

import java.util.UUID

/**
 * Something lasting Mark knows about the user: a person, a preference, where
 * the car keys are. Kept apart from chat history, so clearing the chat never
 * erases it.
 */
data class MemoryFact(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val kind: String = "fact",              // person | preference | fact | place | routine
    val createdAt: Long = System.currentTimeMillis()
)
