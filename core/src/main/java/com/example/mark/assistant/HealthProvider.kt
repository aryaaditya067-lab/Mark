package com.example.mark.assistant

import com.example.mark.model.HealthSnapshot

/**
 * Supplies step and sleep data.
 *
 * The phone reads it from Health Connect, which aggregates Samsung Health and
 * every other fitness app on the device. The watch has no Health Connect, so
 * it reads the phone's most recent snapshot instead.
 */
interface HealthProvider {

    /** False when the platform is missing, or permissions were never granted. */
    suspend fun isAvailable(): Boolean

    /** Steps since midnight. Null when unavailable. */
    suspend fun todaySteps(): Int?

    /** Last night's sleep. Null when nothing was recorded. */
    suspend fun lastNightSleep(): HealthSnapshot?

    /** When the data was captured. Null means it is live. */
    suspend fun capturedAt(): Long? = null
}
