package com.example.mark.assistant

/**
 * Supplies the device's current coordinates.
 * Each app module implements this — the watch and the phone ask for location
 * in different ways, and the core module should not know about either.
 */
interface LocationProvider {

    /** Null when permission is missing or no fix is available. */
    suspend fun currentCoords(): Pair<Double, Double>?

    /** Non-blocking last known location. */
    suspend fun lastKnownCoords(): Pair<Double, Double>? = null

    /** False when the user has not granted location permission. */
    fun hasPermission(): Boolean
}
