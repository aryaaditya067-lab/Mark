package com.example.mark.tool

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
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
    override val definition = FunctionDef(
        name = name,
        description = "Summarize and read recent notifications",
        parameters = Parameters(properties = emptyMap())
    )

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
    override val definition = FunctionDef(
        name = name,
        description = "Read the most recent message from a messaging app",
        parameters = Parameters(properties = emptyMap())
    )

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
    override val definition = FunctionDef(
        name = name,
        description = "Count unread messaging notifications",
        parameters = Parameters(properties = emptyMap())
    )

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
    override val definition = FunctionDef(
        name = name,
        description = "Total count of all unread notifications",
        parameters = Parameters(properties = emptyMap())
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        checkEnabled()?.let { return it }
        val count = MarkNotificationService.getNotifications().size
        return ToolResult.Success("You have $count unread notifications.")
    }
}
