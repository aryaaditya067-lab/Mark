package com.example.mark.repository

import com.example.mark.model.MemoryFact

/**
 * Where Mark keeps what it knows about the user. Implementations live in
 * MemoryRepository.kt: Firestore on the phone, on-device on the watch.
 */
interface MemoryStore {
    suspend fun all(): List<MemoryFact>
    suspend fun add(fact: MemoryFact)
    suspend fun remove(ids: Set<String>)
}
