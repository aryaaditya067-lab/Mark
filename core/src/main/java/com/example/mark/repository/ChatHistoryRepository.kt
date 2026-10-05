package com.example.mark.repository

import com.example.mark.model.Message
import com.google.firebase.firestore.DocumentSnapshot
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
 * Stores the full LLM conversation, including tool calls and tool results,
 * so the model never re-fires a tool it has already run.
 */
class ChatHistoryRepository(
    private val auth: AuthRepository = AuthRepository.instance
) : ChatHistoryStore {

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
                        val list = snapshot?.documents?.mapNotNull { it.toMessage() }
                            ?.reversed() ?: emptyList() // back to oldest-first for the UI
                        trySend(list)
                    }
                awaitClose { registration.remove() }
            }
        }

    /**
     * One-time read of the most recent messages.
     * Used when building a request for the LLM to provide context without downloading
     * the entire history.
     */
    override suspend fun recent(limit: Int): List<Message> {
        val snapshot = collection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()

        return snapshot.documents.mapNotNull { it.toMessage() }
            .reversed() // return in chronological order
    }

    suspend fun append(message: Message) {
        collection().document(message.id).set(message.toFirestore()).await()
    }

    /**
     * Writes a whole turn at once. A turn that dies half way through — a network
     * error between the tool call and the final reply — must leave no trace, or
     * the model reads the orphaned tool call on the next turn and fires it again.
     */
    override suspend fun appendAll(messages: List<Message>) {
        if (messages.isEmpty()) return
        val collection = collection()
        val batch = db.batch()
        messages.forEach { message ->
            batch.set(collection.document(message.id), message.toFirestore())
        }
        // Not awaited: commit() applies locally and queues durably at once, but
        // its Task only completes on the server's ack, never while offline, and
        // waiting held every later turn back in memory.
        batch.commit().addOnFailureListener { android.util.Log.w("MarkHistory", "history write failed", it) }
    }

    override suspend fun clear() {
        val snapshot = collection().get().await()
        // A Firestore batch holds at most 500 writes; a long history needs several.
        snapshot.documents.chunked(MAX_BATCH_WRITES).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private fun DocumentSnapshot.toMessage(): Message? {
        val role = getString("role") ?: return null
        return Message(
            id = id,
            role = role,
            content = getString("content"),
            toolCallsJson = getString("toolCalls"),
            toolCallId = getString("toolCallId"),
            name = getString("name"),
            isToolReply = getBoolean("isToolReply") ?: false,
            createdAt = getLong("createdAt") ?: 0L
        )
    }

    private fun Message.toFirestore(): Map<String, Any?> = hashMapOf(
        "role" to role,
        "content" to content,
        "toolCalls" to toolCallsJson,
        "toolCallId" to toolCallId,
        "name" to name,
        "isToolReply" to isToolReply,
        "createdAt" to createdAt
    )

    companion object {
        private const val MAX_BATCH_WRITES = 500

        val instance: ChatHistoryRepository by lazy { ChatHistoryRepository() }
    }
}
