package com.example.mark.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.mark.model.Reminder
import com.example.mark.repository.Reminders
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reminders kept on the phone and fired by AlarmManager. Stored locally (not
 * in Firestore) because the alarms that fire them are local too, and must be
 * re-armed after a reboot without network or sign-in.
 */
class AndroidReminders(context: Context) : Reminders {

    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("mark_reminders", Context.MODE_PRIVATE)
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    private val gson = Gson()
    private val type = object : TypeToken<List<Reminder>>() {}.type

    override suspend fun add(reminder: Reminder) = withContext(Dispatchers.IO) { store(reminder) }

    /** Stores and schedules; safe to call from a BroadcastReceiver. */
    fun store(reminder: Reminder) {
        synchronized(LOCK) { write(read().filterNot { it.id == reminder.id } + reminder) }
        schedule(reminder)
    }

    override suspend fun list(): List<Reminder> = withContext(Dispatchers.IO) { synchronized(LOCK) { read() } }

    override suspend fun cancel(ids: Set<String>) = withContext(Dispatchers.IO) {
        synchronized(LOCK) { write(read().filterNot { it.id in ids }) }
        ids.forEach { alarms?.cancel(pendingIntent(it)) }
    }

    fun find(id: String): Reminder? = synchronized(LOCK) { read().firstOrNull { it.id == id } }

    /** After firing: a repeating reminder moves to its next time, a one-off is removed. */
    fun fired(id: String, nextAt: Long?) {
        val next = synchronized(LOCK) {
            val all = read()
            val current = all.firstOrNull { it.id == id } ?: return
            val updated = nextAt?.let { current.copy(at = it) }
            write(all.filterNot { it.id == id } + listOfNotNull(updated))
            updated
        }
        next?.let { schedule(it) }
    }

    /**
     * Snooze: a fresh one-off reminder [minutes] from now. It cannot reuse the
     * fired one, which is already gone (one-off) or moved on (repeating).
     */
    fun snooze(text: String, minutes: Int) {
        store(Reminder(text = text, at = System.currentTimeMillis() + minutes * 60_000L))
    }

    /** Alarms do not survive a reboot; BootReceiver calls this. */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        synchronized(LOCK) { read() }.forEach { r ->
            // Missed while the phone was off: fire shortly, rather than never.
            schedule(if (r.at < now) r.copy(at = now + 60_000L) else r)
        }
    }

    private fun schedule(reminder: Reminder) {
        val am = alarms ?: return
        val pi = pendingIntent(reminder.id)
        // Exact when the user allowed "Alarms & reminders"; otherwise a short
        // window, which Doze may stretch by a few minutes.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, reminder.at, WINDOW_MS, pi)
        }
    }

    private fun pendingIntent(id: String): PendingIntent = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun read(): List<Reminder> =
        runCatching { gson.fromJson<List<Reminder>>(prefs.getString(KEY, "[]"), type) }.getOrNull().orEmpty()

    private fun write(all: List<Reminder>) {
        prefs.edit().putString(KEY, gson.toJson(all)).apply()
    }

    private companion object {
        const val KEY = "reminders"
        const val WINDOW_MS = 5 * 60_000L
        val LOCK = Any()
    }
}
