package com.example.mark.repository

import com.example.mark.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * Chat history in Firestore.
 * Path: users/{uid}/chat/{messageId}
 *
 * Stores the full Groq conversation, including tool calls and tool results,
 * so the model never re-fires a tool it has already run.
 */
class ChatHistoryRepository(
    private val auth: AuthRepository = AuthRepository.instance
) {

    private val db = FirebaseFirestore.getInstance()

    private suspend fun collection() =
        db.collection("users").document(auth.ensureSignedIn()).collection("chat")

    /** Real-time stream of the whole conversation, oldest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<List<Message>> = flow { emit(auth.ensureSignedIn()) }
        .flatMapLatest { uid ->
            callbackFlow {
                val registration = db.collection("users").document(uid).collection("chat")
                    // Newest first, capped — the watch does not need months of history,
                    // and pulling it over the Bluetooth proxy takes minutes.
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(40)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        val list = snapshot?.documents?.mapNotNull { doc ->
                            Message(
                                id = doc.id,
                                role = doc.getString("role") ?: return@mapNotNull null,
                                content = doc.getString("content"),
                                toolCallsJson = doc.getString("toolCalls"),
                                toolCallId = doc.getString("toolCallId"),
                                name = doc.getString("name"),
                                isToolReply = doc.getBoolean("isToolReply") ?: false,
                                createdAt = doc.getLong("createdAt") ?: 0L
                            )
                        }?.reversed() ?: emptyList() // back to oldest-first for the UI
                        trySend(list)
                    }
                awaitClose { registration.remove() }
            }
        }

    /**
     * One-time read of the most recent messages.
     * Used when building a request for Groq to provide context without downloading
     * the entire history.
     */
    suspend fun recent(limit: Int): List<Message> {
        val snapshot = collection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            Message(
                id = doc.id,
                role = doc.getString("role") ?: return@mapNotNull null,
                content = doc.getString("content"),
                toolCallsJson = doc.getString("toolCalls"),
                toolCallId = doc.getString("toolCallId"),
                name = doc.getString("name"),
                isToolReply = doc.getBoolean("isToolReply") ?: false,
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        }.reversed() // return in chronological order
    }

    suspend fun append(message: Message) {
        val data = hashMapOf<String, Any?>(
            "role" to message.role,
            "content" to message.content,
            "toolCalls" to message.toolCallsJson,
            "toolCallId" to message.toolCallId,
            "name" to message.name,
            "isToolReply" to message.isToolReply,
            "createdAt" to message.createdAt
        )
        collection().document(message.id).set(data).await()
    }

    /**
     * Writes a whole turn at once. A turn that dies half way through — a network
     * error between the tool call and the final reply — must leave no trace, or
     * the model reads the orphaned tool call on the next turn and fires it again.
     */
    suspend fun appendAll(messages: List<Message>) {
        if (messages.isEmpty()) return
        val collection = collection()
        val batch = db.batch()
        messages.forEach { message ->
            val data = hashMapOf<String, Any?>(
                "role" to message.role,
                "content" to message.content,
                "toolCalls" to message.toolCallsJson,
                "toolCallId" to message.toolCallId,
                "name" to message.name,
                "createdAt" to message.createdAt
            )
            batch.set(collection.document(message.id), data)
        }
        batch.commit().await()
    }

    suspend fun clear() {
        val snapshot = collection().get().await()
        val batch = db.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    companion object {
        val instance: ChatHistoryRepository by lazy { ChatHistoryRepository() }
    }
}
