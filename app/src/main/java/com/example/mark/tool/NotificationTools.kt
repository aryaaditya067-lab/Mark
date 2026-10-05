package com.example.mark.tool

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.mark.assistant.PhoneToolSchemas
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.router.IntentType
import com.example.mark.service.MarkNotificationService

abstract class BaseNotificationTool(protected val context: Context) : Tool {
    protected fun checkEnabled(): ToolResult? {
        if (!MarkNotificationService.isEnabled(context)) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ToolResult.Failure(
                "I need notification access — opening settings. Please enable Mark and try again.",
                reason = "no_permission"
            )
        }
        return null
    }

    protected val messagingPackages = setOf(
        "com.whatsapp",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "org.telegram.messenger"
    )
}

class ReadNotificationsTool(context: Context) : BaseNotificationTool(context) {
    override val name = "read_notifications"
    override val intent = IntentType.READ_NOTIFICATIONS
    override val definition = PhoneToolSchemas.READ_NOTIFICATIONS

    override suspend fun execute(request: ToolRequest): ToolResult {
        checkEnabled()?.let { return it }
        val notifications = MarkNotificationService.getNotifications()
        if (notifications.isEmpty()) return ToolResult.Success("You have no new notifications.")

        val summary = notifications.take(5)
            .groupBy { it.appLabel }
            .map { (label, list) -> "${list.size} from $label" }
            .joinToString(", ")

        val details = notifications.take(2).joinToString("\n") { 
            "From ${it.appLabel}: ${it.title}. ${it.text}"
        }

        return ToolResult.Success("You have $summary.\n\n$details")
    }
}

class ReadLastMessageTool(context: Context) : BaseNotificationTool(context) {
    override val name = "read_last_message"
    override val intent = IntentType.READ_LAST_MESSAGE
    override val definition = PhoneToolSchemas.READ_LAST_MESSAGE

    override suspend fun execute(request: ToolRequest): ToolResult {
        checkEnabled()?.let { return it }
        val msg = MarkNotificationService.getNotifications()
            .firstOrNull { it.packageName in messagingPackages }
            ?: return ToolResult.Success("No recent messages found.")

        return ToolResult.Success("Last message from ${msg.appLabel}. ${msg.title} says: ${msg.text}")
    }
}

class CheckNewMessagesTool(context: Context) : BaseNotificationTool(context) {
    override val name = "check_new_messages"
    override val intent = IntentType.CHECK_NEW_MESSAGES
    override val definition = PhoneToolSchemas.CHECK_NEW_MESSAGES

    override suspend fun execute(request: ToolRequest): ToolResult {
        checkEnabled()?.let { return it }
        val count = MarkNotificationService.getNotifications().count { it.packageName in messagingPackages }
        if (count == 0) return ToolResult.Success("No new messages.")
        return ToolResult.Success("You have $count unread messages.")
    }
}

class UnreadCountTool(context: Context) : BaseNotificationTool(context) {
    override val name = "unread_count"
    override val intent = IntentType.UNREAD_COUNT
    override val definition = PhoneToolSchemas.UNREAD_COUNT

    override suspend fun execute(request: ToolRequest): ToolResult {
        checkEnabled()?.let { return it }
        val count = MarkNotificationService.getNotifications().size
        return ToolResult.Success("You have $count unread notifications.")
    }
}

class ReadConversationTool(context: Context) : BaseNotificationTool(context) {
    override val name = "read_conversation"
    override val intent = IntentType.READ_CONVERSATION
    override val definition = PhoneToolSchemas.READ_CONVERSATION

    override suspend fun execute(request: ToolRequest): ToolResult {
        checkEnabled()?.let { return it }
        val contact = request.string("contact") ?: return ToolResult.Failure("Whose messages, sir?", reason = "missing_arg")
        val chat = com.example.mark.utils.Conversations.find(MarkNotificationService.getConversations(), contact, request.string("app"))
            ?: return ToolResult.Failure("No unread chat from $contact right now.", reason = "not_found")
        return ToolResult.Success(com.example.mark.utils.Conversations.describe(chat), mapOf("contact" to chat.title, "app" to chat.app))
    }
}

/** Reply inside WhatsApp/Telegram/Messages via the notification's reply action, after a spoken yes. */
class ReplyMessageTool(context: Context) : BaseNotificationTool(context) {
    override val name = "reply_to_message"
    override val intent = IntentType.REPLY_MESSAGE
    override val definition = PhoneToolSchemas.REPLY_MESSAGE

    override fun needsConfirmation(request: ToolRequest) = true

    override suspend fun execute(request: ToolRequest): ToolResult {
        checkEnabled()?.let { return it }
        val contact = request.string("contact") ?: return ToolResult.Failure("Reply to whom, sir?", reason = "missing_arg")
        val text = request.string("text") ?: return ToolResult.Failure("What should I reply?", reason = "missing_arg")
        val chat = com.example.mark.utils.Conversations.find(MarkNotificationService.getConversations(), contact, request.string("app"))
            ?: return ToolResult.Failure("I can only reply while $contact's message notification is showing.", reason = "not_found")
        if (!chat.canReply) return ToolResult.Failure("${chat.app} doesn't allow replies from its notification for this chat.", reason = "no_reply_action")
        return if (MarkNotificationService.reply(context, chat.key, text)) {
            ToolResult.Success("Replied to ${chat.title} on ${chat.app}.")
        } else {
            ToolResult.Failure("The reply didn't go through; the notification may have been dismissed.", reason = "send_failed")
        }
    }
}
