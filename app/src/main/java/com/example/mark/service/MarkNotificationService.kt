package com.example.mark.service

import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.mark.MarkApplication
import com.example.mark.utils.ChatLine
import com.example.mark.utils.Conversation
import com.example.mark.utils.Conversations
import com.example.mark.utils.SELF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class MarkNotification(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val isOngoing: Boolean,
    val category: String?,
    val key: String = ""
)

class MarkNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val settings = (application as? MarkApplication)?.settingsRepository ?: return
        scope.launch { settings.announceMessages.collect { announceEnabled = it } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** The buffer used to start empty after every restart even with a full shade. */
    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching { activeNotifications }.getOrNull()?.forEach { ingest(it, announce = false) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = ingest(sbn, announce = true)

    private fun ingest(sbn: StatusBarNotification, announce: Boolean) {
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
            category = sbn.notification.category,
            key = sbn.key
        )

        synchronized(lock) {
            // Remove older version from same app if titles match to avoid duplicates from same conversation
            buffer.removeAll { it.packageName == notification.packageName && it.title == notification.title }
            buffer.add(0, notification)
            if (buffer.size > 30) {
                buffer.removeAt(buffer.size - 1)
            }
        }

        if (sbn.packageName in MESSAGING_PACKAGES) {
            val conversation = conversationOf(sbn, appLabel, title, text) ?: return
            val previous = synchronized(lock) {
                val old = conversations[sbn.key]
                conversations[sbn.key] = conversation
                replyActions[sbn.key] = replyActionOf(sbn.notification)
                old
            }
            if (announce && previous?.lines?.lastOrNull() != conversation.lines.lastOrNull()) maybeAnnounce(conversation)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(lock) {
            // By key: matching on postTime missed entries whose newer version replaced them.
            buffer.removeAll { it.key == sbn.key }
            conversations.remove(sbn.key)
            replyActions.remove(sbn.key)
        }
    }

    /** History from MessagingStyle when the app provides it, else the visible title/text. */
    private fun conversationOf(sbn: StatusBarNotification, app: String, title: String, text: String): Conversation? {
        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification) }.getOrNull()
        val lines = style?.messages?.mapNotNull { m ->
            val body = m.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // MessagingStyle marks the user's own messages with no person.
            ChatLine(sender = m.person?.name?.toString() ?: SELF, text = body, time = m.timestamp)
        }.orEmpty().ifEmpty { listOf(ChatLine(title, text, sbn.postTime)) }
        val name = style?.conversationTitle?.toString()?.takeIf { it.isNotBlank() } ?: title
        if (name.isBlank()) return null
        return Conversation(
            key = sbn.key, app = app, packageName = sbn.packageName, title = name,
            lines = lines.takeLast(20), canReply = replyActionOf(sbn.notification) != null, postTime = sbn.postTime
        )
    }

    private fun replyActionOf(notification: Notification): Notification.Action? =
        notification.actions?.firstOrNull { action ->
            action.remoteInputs?.any { it.allowFreeFormInput } == true
        }

    /**
     * Opt-in, and only into headphones: a message line spoken out of the phone
     * speaker in a room is a privacy leak. Never in quiet hours, never with
     * Do Not Disturb on, never over Mark himself, at most every 20 seconds.
     */
    private fun maybeAnnounce(c: Conversation) {
        if (!announceEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastAnnouncedAt < 20_000) return
        if (Conversations.inQuietHours(java.time.LocalTime.now().hour)) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        if (!headphonesConnected()) return
        val tts = (application as? MarkApplication)?.ttsManager ?: return
        if (tts.isSpeaking.value) return
        val line = Conversations.announcement(c) ?: return
        lastAnnouncedAt = now
        tts.speak(line)
    }

    private fun headphonesConnected(): Boolean {
        val am = getSystemService(AudioManager::class.java) ?: return false
        val types = mutableSetOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) types += AudioDeviceInfo.TYPE_BLE_HEADSET
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    }

    private fun shouldSkip(sbn: StatusBarNotification): Boolean {
        if (sbn.isOngoing) return true
        if (sbn.packageName == packageName) return true
        val category = sbn.notification.category
        if (category == Notification.CATEGORY_SERVICE || category == Notification.CATEGORY_TRANSPORT) return true
        // Group summaries ("5 new messages") duplicate the real conversations.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return true
        return false
    }

    companion object {
        private val lock = Any()
        private val buffer = mutableListOf<MarkNotification>()
        private val conversations = linkedMapOf<String, Conversation>()
        private val replyActions = mutableMapOf<String, Notification.Action?>()
        @Volatile private var announceEnabled = false
        @Volatile private var lastAnnouncedAt = 0L

        val MESSAGING_PACKAGES = setOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "com.google.android.apps.messaging", "com.samsung.android.messaging",
            "org.telegram.messenger", "com.instagram.android", "com.facebook.orca"
        )

        fun getNotifications(): List<MarkNotification> = synchronized(lock) { buffer.toList() }

        fun getConversations(): List<Conversation> = synchronized(lock) { conversations.values.toList() }

        /**
         * Replies through the notification's own reply action. Works only while
         * that notification is still showing.
         */
        fun reply(context: Context, conversationKey: String, text: String): Boolean {
            val action = synchronized(lock) { replyActions[conversationKey] } ?: return false
            val inputs = action.remoteInputs ?: return false
            val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
            val intent = Intent()
            RemoteInput.addResultsToIntent(inputs, intent, results)
            return runCatching { action.actionIntent.send(context, 0, intent) }.isSuccess
        }

        fun isEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            val componentName = ComponentName(context, MarkNotificationService::class.java)
            return enabledListeners?.contains(componentName.flattenToString()) == true
        }
    }
}
