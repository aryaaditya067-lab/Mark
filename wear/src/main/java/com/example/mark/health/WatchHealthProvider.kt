package com.example.mark.health

import android.content.Context
import com.example.mark.assistant.HealthProvider
import com.example.mark.model.HealthSnapshot

/**
 * Health provider for the watch. Tries local sensors first (Health Services),
 * falls back to the phone's Firestore snapshot.
 */
class WatchHealthProvider(
    context: Context,
    private val remote: HealthProvider = RemoteHealthProvider()
) : HealthProvider {

    private val local = LocalHealthReader(context)

    override suspend fun isAvailable(): Boolean = true

    override suspend fun todaySteps(): Int? {
        return local.todaySteps() ?: remote.todaySteps()
    }

    override suspend fun lastNightSleep(): HealthSnapshot? {
        val minutes = local.lastNightSleep()
        if (minutes != null) {
            return HealthSnapshot(
                steps = null,
                sleepMinutes = minutes,
                capturedAt = System.currentTimeMillis()
            )
        }
        return remote.lastNightSleep()
    }

    override suspend fun capturedAt(): Long? = remote.capturedAt()
}
