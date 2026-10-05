package com.example.mark.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.mark.MainActivity
import com.example.mark.utils.ReminderTimes

/**
 * Fires a reminder as a notification with Done and Snooze actions. Phone
 * notifications are mirrored to the paired watch, which buzzes on the wrist.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val reminders = AndroidReminders(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        when (intent.action) {
            ACTION_DONE -> manager.cancel(id.hashCode())
            ACTION_SNOOZE -> {
                manager.cancel(id.hashCode())
                intent.getStringExtra(EXTRA_TEXT)?.let { reminders.snooze(it, SNOOZE_MINUTES) }
            }
            else -> {
                val reminder = reminders.find(id) ?: return
                show(context, manager, id, reminder.text)
                reminders.fired(id, ReminderTimes.next(reminder.at, reminder.repeat, ReminderTimes.now()))
            }
        }
    }

    private fun show(context: Context, manager: NotificationManager, id: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(manager)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Sir, a reminder")
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(action(context, id, text, ACTION_DONE, "Done"))
            .addAction(action(context, id, text, ACTION_SNOOZE, "Snooze $SNOOZE_MINUTES min"))
            .build()
        manager.notify(id.hashCode(), notification)
    }

    private fun action(context: Context, id: String, text: String, action: String, label: String): android.app.Notification.Action {
        val pi = PendingIntent.getBroadcast(
            context, (id + action).hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(action)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_TEXT, text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return android.app.Notification.Action.Builder(null, label, pi).build()
    }

    companion object {
        const val ACTION_FIRE = "com.example.mark.reminder.FIRE"
        const val ACTION_DONE = "com.example.mark.reminder.DONE"
        const val ACTION_SNOOZE = "com.example.mark.reminder.SNOOZE"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TEXT = "reminder_text"
        const val CHANNEL_ID = "mark_reminders"
        private const val SNOOZE_MINUTES = 10

        fun ensureChannel(manager: NotificationManager) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Reminders you asked Mark to set"
                    }
                )
            }
        }
    }
}

/** Alarms are cleared on reboot and on app update; put the reminders back. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AndroidReminders(context).rescheduleAll()
        }
    }
}
