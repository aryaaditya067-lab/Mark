package com.example.mark.repository

import com.example.mark.model.Message

/** The conversation log the assistant reads context from and appends turns to. */
interface ChatHistoryStore {
    /** The newest [limit] messages, oldest first. */
    suspend fun recent(limit: Int): List<Message>
    suspend fun appendAll(messages: List<Message>)
    suspend fun clear()
}
