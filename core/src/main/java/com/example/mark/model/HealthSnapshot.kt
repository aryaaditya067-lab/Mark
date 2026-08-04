package com.example.mark.model

/**
 * A point-in-time view of the user's health data.
 * Written by the phone, read by the watch.
 */
data class HealthSnapshot(
    val steps: Int? = null,
    val sleepMinutes: Int? = null,
    val sleepStart: Long? = null,
    val sleepEnd: Long? = null,
    val capturedAt: Long = System.currentTimeMillis()
)
