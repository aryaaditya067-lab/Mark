package com.example.mark.repository

import android.content.Context
import com.example.mark.model.MemoryFact
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Phone: Firestore, path users/{uid}/memory/{id}, separate from chat history.
 * Read once per process and then served from memory, so building a prompt
 * never waits on the network after the first time.
 */
class FirestoreMemoryStore(
    private val auth: AuthRepository = AuthRepository.instance
) : MemoryStore {

    private val db = FirebaseFirestore.getInstance()
    private val lock = Mutex()
    private var cache: List<MemoryFact>? = null

    private suspend fun collection() =
        db.collection("users").document(auth.ensureSignedIn()).collection("memory")

    override suspend fun all(): List<MemoryFact> = lock.withLock {
        cache ?: collection().get().await().documents.mapNotNull { doc ->
            MemoryFact(
                id = doc.id,
                text = doc.getString("text") ?: return@mapNotNull null,
                kind = doc.getString("kind") ?: "fact",
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        }.also { cache = it }
    }

    override suspend fun add(fact: MemoryFact) {
        all() // make sure the cache is loaded
        // The cache is updated from its CURRENT value under the lock: tool calls
        // in one round run in parallel, and computing from a snapshot taken
        // before the network write lost one of two concurrent changes.
        val dropped = lock.withLock {
            val current = cache.orEmpty()
            val kept = MemoryFacts.withAdded(current, fact)
            cache = kept
            current.map { it.id }.toSet() - kept.map { it.id }.toSet()
        }
        collection().document(fact.id).set(
            mapOf("text" to fact.text, "kind" to fact.kind, "createdAt" to fact.createdAt)
        ).await()
        dropped.forEach { collection().document(it).delete().await() }
    }

    override suspend fun remove(ids: Set<String>) {
        lock.withLock { cache = cache?.filterNot { it.id in ids } }
        ids.forEach { collection().document(it).delete().await() }
    }
}

/**
 * Watch: on-device only. The watch deliberately never touches Firestore (its
 * network runs over the phone's Bluetooth proxy and startup is CPU-bound), so
 * what it learns stays on the watch.
 */
class LocalMemoryStore(context: Context) : MemoryStore {

    private val prefs = context.applicationContext.getSharedPreferences("mark_memory", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<MemoryFact>>() {}.type
    private val lock = Mutex()

    private fun read(): List<MemoryFact> =
        runCatching { gson.fromJson<List<MemoryFact>>(prefs.getString(KEY, "[]"), type) }
            .getOrNull().orEmpty()

    private fun write(facts: List<MemoryFact>) {
        prefs.edit().putString(KEY, gson.toJson(facts)).apply()
    }

    override suspend fun all(): List<MemoryFact> = withContext(Dispatchers.IO) { lock.withLock { read() } }

    override suspend fun add(fact: MemoryFact) = withContext(Dispatchers.IO) {
        lock.withLock { write(MemoryFacts.withAdded(read(), fact)) }
    }

    override suspend fun remove(ids: Set<String>) = withContext(Dispatchers.IO) {
        lock.withLock { write(read().filterNot { it.id in ids }) }
    }

    private companion object { const val KEY = "facts" }
}
