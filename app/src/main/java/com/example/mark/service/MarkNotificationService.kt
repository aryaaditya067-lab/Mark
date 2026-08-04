package com.example.mark.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class MarkNotification(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val isOngoing: Boolean,
    val category: String?
)

class MarkNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (shouldSkip(sbn)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        
        if (title.isEmpty() && text.isEmpty()) return

        val pm = packageManager
        val appLabel = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val notification = MarkNotification(
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = title,
            text = text,
            postTime = sbn.postTime,
            isOngoing = sbn.isOngoing,
            category = sbn.notification.category
        )

        synchronized(lock) {
            // Remove older version from same app if titles match to avoid duplicates from same conversation
            buffer.removeAll { it.packageName == notification.packageName && it.title == notification.title }
            buffer.add(0, notification)
            if (buffer.size > 30) {
                buffer.removeAt(buffer.size - 1)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(lock) {
            buffer.removeAll { it.packageName == sbn.packageName && it.postTime == sbn.postTime }
        }
    }

    private fun shouldSkip(sbn: StatusBarNotification): Boolean {
        if (sbn.isOngoing) return true
        if (sbn.packageName == packageName) return true
        val category = sbn.notification.category
        if (category == Notification.CATEGORY_SERVICE || category == Notification.CATEGORY_TRANSPORT) return true
        return false
    }

    companion object {
        private val lock = Any()
        private val buffer = mutableListOf<MarkNotification>()

        fun getNotifications(): List<MarkNotification> = synchronized(lock) { buffer.toList() }
        
        fun isEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            val componentName = ComponentName(context, MarkNotificationService::class.java)
            return enabledListeners?.contains(componentName.flattenToString()) == true
        }
    }
}
