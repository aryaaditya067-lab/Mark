package com.example.mark.repository

import com.example.mark.model.Reminder

/** Scheduled reminders. Implemented on the phone (alarms + notifications). */
interface Reminders {
    suspend fun add(reminder: Reminder)
    suspend fun list(): List<Reminder>
    suspend fun cancel(ids: Set<String>)
}
