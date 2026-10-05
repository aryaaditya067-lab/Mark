package com.example.mark.assistant

import com.example.mark.router.ReplyMode

/**
 * Events emitted by the AssistantController during a turn.
 */
sealed interface AssistantEvent {
    /** A chunk of natural language text. */
    data class Text(
        val content: String,
        val mode: ReplyMode = ReplyMode.SPEAK
    ) : AssistantEvent
    
    /**
     * The user ended the conversation ("bas", "bye"). Emitted before [Done];
     * voice surfaces let the goodbye finish speaking, then stop listening.
     */
    data object EndSession : AssistantEvent

    /** The turn has finished. */
    data object Done : AssistantEvent
    
    /** Something went wrong. */
    data class Error(val throwable: Throwable) : AssistantEvent
}
