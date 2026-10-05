package com.example.mark.model

import java.util.UUID

/** A reminder that fires as a notification on the phone (and so on the watch). */
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val at: Long,                           // epoch millis of the next firing
    val repeat: String = "none",            // none | daily | weekly
    val createdAt: Long = System.currentTimeMillis()
)
