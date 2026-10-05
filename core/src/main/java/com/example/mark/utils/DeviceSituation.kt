package com.example.mark.utils

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.mark.assistant.SituationProvider
import com.example.mark.repository.SettingsRepository
import com.example.mark.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What this device can cheaply tell about the user's situation. Every part is
 * optional: a missing permission or a failed read just leaves its line out.
 *
 * @param tasks null on the watch, which never touches Firestore.
 */
class DeviceSituation(
    context: Context,
    private val isWatch: Boolean,
    private val settings: SettingsRepository?,
    private val tasks: TaskRepository?,
) : SituationProvider {

    private val context = context.applicationContext

    override suspend fun snapshot(): List<String> = withContext(Dispatchers.IO) {
        listOfNotNull(
            runCatching { userName() }.getOrNull(),
            runCatching { battery() }.getOrNull(),
            runCatching { if (isWatch) null else nextEvents() }.getOrNull(),
            runCatching { pendingTasks() }.getOrNull(),
        )
    }

    private suspend fun userName(): String? =
        settings?.userName?.first()?.takeIf { it.isNotBlank() }
            ?.let { "The user's name is $it; keep the usual form of address unless asked otherwise." }

    private fun battery(): String? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val device = if (isWatch) "Watch" else "Phone"
        return "$device battery: ${level * 100 / scale}%" + if (charging) ", charging." else "."
    }

    /** Instances, not Events: recurring meetings only exist as instances. */
    private fun nextEvents(): String? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val now = System.currentTimeMillis()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, now + LOOKAHEAD_MS)
        val uri = builder.build()
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY)
        val events = mutableListOf<String>()
        context.contentResolver.query(
            uri, projection, "${CalendarContract.Instances.BEGIN} >= ?", arrayOf(now.toString()),
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext() && events.size < 2) {
                val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                if (c.getInt(2) == 1) continue // all-day entries are not "next"
                events += "$title ${describe(c.getLong(1))}"
            }
        }
        return if (events.isEmpty()) "No more calendar events in the next day and a half."
        else "Next events: " + events.joinToString("; ") + "."
    }

    private suspend fun pendingTasks(): String? {
        val repo = tasks ?: return null
        val pending = repo.getTasksOnce(onlyPending = true)
        return when (pending.size) {
            0 -> "No pending tasks."
            else -> "Pending tasks: ${pending.size} (" + pending.take(3).joinToString(", ") { it.title } +
                (if (pending.size > 3) ", ..." else "") + ")."
        }
    }

    private fun describe(millis: Long): String {
        val zone = ZoneId.systemDefault()
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val today = LocalDate.now(zone)
        val day = when (at.toLocalDate()) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> at.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))
        }
        return "$day at ${at.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }

    private companion object {
        const val LOOKAHEAD_MS = 36 * 60 * 60 * 1000L
    }
}
