package com.example.mark.health

import com.example.mark.assistant.HealthProvider
import com.example.mark.model.HealthSnapshot
import com.example.mark.repository.HealthRepository

/**
 * Reads the snapshot the phone last wrote. Used on devices that have no
 * Health Connect of their own — currently the watch.
 */
class RemoteHealthProvider(
    private val repository: HealthRepository = HealthRepository.instance
) : HealthProvider {

    /**
     * Over the watch's Bluetooth link every Firestore read costs a round trip,
     * so one tool call must not turn into three. Cached for the length of a turn.
     */
    private var cached: HealthSnapshot? = null
    private var cachedAt: Long = 0

    /** Null when the phone has never synced, or the data is from a previous day. */
    private suspend fun snapshot(): HealthSnapshot? {
        val now = System.currentTimeMillis()
        if (cached != null && now - cachedAt < 30_000) return cached

        val snap = runCatching { repository.read() }.getOrNull() ?: return null
        if (!isFromToday(snap.capturedAt)) return null

        cached = snap
        cachedAt = now
        return snap
    }

    override suspend fun isAvailable(): Boolean = snapshot() != null

    override suspend fun todaySteps(): Int? = snapshot()?.steps

    override suspend fun lastNightSleep(): HealthSnapshot? =
        snapshot()?.takeIf { it.sleepMinutes != null }

    override suspend fun capturedAt(): Long? = snapshot()?.capturedAt

    private fun isFromToday(millis: Long): Boolean {
        if (millis == 0L) return false
        val zone = java.time.ZoneId.systemDefault()
        val captured = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return captured == java.time.LocalDate.now(zone)
    }
}
