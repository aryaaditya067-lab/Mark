package com.example.mark.repository

import com.example.mark.model.Task
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
 * Tasks backed by Firestore.
 * Path: users/{uid}/tasks/{taskId}
 */
class TaskRepository(
    private val auth: AuthRepository = AuthRepository.instance
) {
    /** One-time read (not a stream). Used by tool calls. */
    suspend fun getTasksOnce(onlyPending: Boolean = true): List<Task> {
        val snapshot = collection().get().await()
        return snapshot.documents.mapNotNull { doc ->
            Task(
                id = doc.id,
                title = doc.getString("title") ?: return@mapNotNull null,
                description = doc.getString("description") ?: "",
                dueDate = doc.getString("dueDate") ?: "",
                isCompleted = doc.getBoolean("isCompleted") ?: false,
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        }.filter { if (onlyPending) !it.isCompleted else true }
    }

    /** Finds a pending task by title (case-insensitive) and marks it complete. */
    suspend fun completeTaskByTitle(title: String): Boolean {
        val match = getTasksOnce(onlyPending = true)
            .firstOrNull { it.title.equals(title.trim(), ignoreCase = true) }
            ?: return false
        collection().document(match.id).update("isCompleted", true).await()
        return true
    }

    suspend fun completeTaskByIndex(index: Int): Boolean {
        val tasks = getTasksOnce(onlyPending = true).sortedByDescending { it.createdAt }
        val match = tasks.getOrNull(index) ?: return false
        collection().document(match.id).update("isCompleted", true).await()
        return true
    }
    private val db = FirebaseFirestore.getInstance()

    private suspend fun collection() =
        db.collection("users").document(auth.ensureSignedIn()).collection("tasks")

    /** Real-time stream. Emits a new list whenever Firestore changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: Flow<List<Task>> = flow { emit(auth.ensureSignedIn()) }
        .flatMapLatest { uid ->
            callbackFlow {
                val registration = db.collection("users").document(uid).collection("tasks")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        val list = snapshot?.documents?.mapNotNull { doc ->
                            Task(
                                id = doc.id,
                                title = doc.getString("title") ?: return@mapNotNull null,
                                description = doc.getString("description") ?: "",
                                isCompleted = doc.getBoolean("isCompleted") ?: false,
                                createdAt = doc.getLong("createdAt") ?: 0L
                            )
                        } ?: emptyList()
                        trySend(list)
                    }
                awaitClose { registration.remove() }
            }
        }

    suspend fun addTask(title: String, dueDate: String = "", description: String = "") {
        if (title.isBlank()) return
        val data = hashMapOf(
            "title" to title.trim(),
            "dueDate" to dueDate.trim(),
            "description" to description.trim(),
            "isCompleted" to false,
            "createdAt" to System.currentTimeMillis()
        )
        collection().add(data).await()
    }

    suspend fun toggleTask(id: String, currentlyCompleted: Boolean) {
        collection().document(id).update("isCompleted", !currentlyCompleted).await()
    }

    suspend fun deleteTask(id: String) {
        collection().document(id).delete().await()
    }

    companion object {
        val instance: TaskRepository by lazy { TaskRepository() }
    }
}