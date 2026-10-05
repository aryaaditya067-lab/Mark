package com.example.mark.brief

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.mark.MainActivity
import com.example.mark.model.Reminder
import com.example.mark.reminder.AndroidReminders
import com.example.mark.repository.SettingsRepository
import com.example.mark.repository.WeatherRepository
import com.example.mark.utils.BriefText
import com.example.mark.utils.ForecastSummary
import com.example.mark.utils.PlayLocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The daily brief, pushed as a notification at the time set in Settings, plus
 * a heads-up 10 minutes before each of today's meetings. Built offline from
 * templates; the phone's notification is mirrored to the watch.
 */
object MorningBrief {

    const val CHANNEL_ID = "mark_brief"
    private const val PREFS = "mark_brief"
    private const val KEY_LAST = "last"

    /** The text of the most recent brief, for its Listen action. */
    fun lastBrief(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)
    private const val REQUEST_CODE = 7301
    private const val HEADS_UP_MINUTES = 10L

    /** Arms (or disarms) tomorrow's/today's brief alarm from the saved settings. */
    suspend fun schedule(context: Context) {
        val settings = SettingsRepository(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        if (!settings.briefEnabled.first()) {
            alarms.cancel(pi)
            return
        }
        val time = runCatching { LocalTime.parse(settings.briefTime.first()) }.getOrDefault(LocalTime.of(7, 30))
        val now = ZonedDateTime.now()
        var at = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!at.isAfter(now)) at = at.plusDays(1)
        // A brief a few minutes late is fine; no exact-alarm permission needed.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), 10 * 60_000L, pi)
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, BriefReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Gathers today's facts and posts the brief. Every source is optional and time-boxed. */
    suspend fun post(context: Context) {
        val settings = SettingsRepository(context)
        val zone = ZoneId.systemDefault()
        val events = todaysEvents(context, zone)
        scheduleHeadsUps(context, events)
        val weather = withTimeoutOrNull(6_000) { forecast(context, zone) }
        val reminders = runCatching { AndroidReminders(context).list() }.getOrDefault(emptyList())
            .filter { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.at), zone).toLocalDate() == LocalDate.now(zone) }
            .filterNot { it.id.startsWith(HEADS_UP_PREFIX) }
            .sortedBy { it.at }
            .map { "${it.text} at ${clock(it.at, zone)}" }
        val text = BriefText.compose(
            hour = ZonedDateTime.now(zone).hour,
            name = settings.userName.first(),
            weather = weather,
            events = events.map { BriefText.Event(it.title, clock(it.begin, zone)) },
            reminders = reminders,
            batteryPercent = battery(context)
        )
        notify(context, text)
    }

    private data class CalendarEvent(val id: Long, val title: String, val begin: Long)

    /** Instances, not Events: recurring meetings only exist as instances. */
    private fun todaysEvents(context: Context, zone: ZoneId): List<CalendarEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, start)
        ContentUris.appendId(builder, end)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY
        )
        val events = mutableListOf<CalendarEvent>()
        runCatching {
            context.contentResolver.query(builder.build(), projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(3) == 1) continue
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    events += CalendarEvent(c.getLong(0), title, c.getLong(2))
                }
            }
        }
        return events
    }

    private const val HEADS_UP_PREFIX = "meeting:"

    /** Re-reads today's calendar and (re)arms meeting heads-ups; cheap, call at app start. */
    suspend fun refreshHeadsUps(context: Context) {
        scheduleHeadsUps(context, todaysEvents(context, ZoneId.systemDefault()))
    }

    /** "Standup in 10 minutes" — through the reminder system, deduplicated per event instance. */
    private suspend fun scheduleHeadsUps(context: Context, events: List<CalendarEvent>) {
        val reminders = AndroidReminders(context)
        val now = System.currentTimeMillis()
        events.forEach { e ->
            val at = e.begin - HEADS_UP_MINUTES * 60_000L
            if (at > now) {
                reminders.add(Reminder(id = "$HEADS_UP_PREFIX${e.id}:${e.begin}", text = "${e.title} in $HEADS_UP_MINUTES minutes", at = at))
            }
        }
    }

    private suspend fun forecast(context: Context, zone: ZoneId): String? {
        val coords = PlayLocationProvider(context).lastKnownCoords() ?: return null
        val forecast = WeatherRepository.instance.forecastByCoords(coords.first, coords.second).getOrNull() ?: return null
        return ForecastSummary.forDay(forecast.list.orEmpty(), LocalDate.now(zone), forecast.city?.timezone ?: 0, forecast.city?.name ?: "your area")
    }

    private fun battery(context: Context): Int? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) null else level * 100 / scale
    }

    private fun clock(millis: Long, zone: ZoneId): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

    private fun notify(context: Context, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Morning brief", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST, text).apply()
        val listen = PendingIntent.getActivity(
            context, REQUEST_CODE, MainActivity.readBriefIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(text.substringBefore('.') + ".")
            .setContentText(text.substringAfter(". ", text))
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(listen)
            .addAction(android.app.Notification.Action.Builder(null, "Listen", listen).build())
            .build()
        manager.notify(REQUEST_CODE, notification)
    }
}

/** Fires daily: post the brief, then arm tomorrow's. */
class BriefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000) { MorningBrief.post(context.applicationContext) }
                MorningBrief.schedule(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
