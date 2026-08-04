package com.example.mark.repository

import com.example.mark.model.HealthSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * The phone's latest health snapshot, shared with the watch.
 * Path: users/{uid}/health/latest
 *
 * Health Connect lives only on the phone, so the watch reads what the phone
 * last wrote rather than measuring anything itself.
 */
class HealthRepository(
    private val auth: AuthRepository = AuthRepository.instance
) {

    private val db = FirebaseFirestore.getInstance()

    private suspend fun doc() =
        db.collection("users").document(auth.ensureSignedIn())
            .collection("health").document("latest")

    suspend fun write(snapshot: HealthSnapshot) {
        val data = hashMapOf<String, Any?>(
            "steps" to snapshot.steps,
            "sleepMinutes" to snapshot.sleepMinutes,
            "sleepStart" to snapshot.sleepStart,
            "sleepEnd" to snapshot.sleepEnd,
            "capturedAt" to snapshot.capturedAt
        )
        doc().set(data).await()
    }

    suspend fun read(): HealthSnapshot? {
        val snap = doc().get().await()
        if (!snap.exists()) return null
        return HealthSnapshot(
            steps = snap.getLong("steps")?.toInt(),
            sleepMinutes = snap.getLong("sleepMinutes")?.toInt(),
            sleepStart = snap.getLong("sleepStart"),
            sleepEnd = snap.getLong("sleepEnd"),
            capturedAt = snap.getLong("capturedAt") ?: 0L
        )
    }

    companion object {
        val instance: HealthRepository by lazy { HealthRepository() }
    }
}
