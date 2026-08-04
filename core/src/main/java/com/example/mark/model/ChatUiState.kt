package com.example.mark.model

data class ChatUiState(
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val streamingReply: String? = null,
    val errorMessage: String? = null
)
