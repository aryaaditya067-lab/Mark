package com.example.mark.model

/**
 * Hands-free conversation. Each reply is followed by the microphone opening
 * again, so the user never has to touch the screen.
 */
data class VoiceModeState(
    val active: Boolean = false,
    val phase: VoicePhase = VoicePhase.IDLE,
    val transcript: String = "",       // what the user is saying right now
    val lastReply: String = "",        // what Mark just said
    val amplitude: Float = 0f,
    val errorMessage: String? = null
)

enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING }
