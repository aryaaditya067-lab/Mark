package com.example.mark.tool

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.mark.assistant.PhoneToolSchemas
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.router.IntentType
import com.example.mark.service.MarkAccessibilityService

class ScreenshotTool(private val context: Context) : Tool {
    override val name = "take_screenshot"
    override val intent = IntentType.TAKE_SCREENSHOT
    override val definition = PhoneToolSchemas.TAKE_SCREENSHOT

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ToolResult.Failure("Screenshot via voice requires Android 11 or higher.", reason = "version_unsupported")
        }

        if (!MarkAccessibilityService.isEnabled) {
            runCatching {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            return ToolResult.Failure(
                "Mark needs Accessibility permission to take screenshots. I've opened the settings — please enable it.",
                reason = "no_permission"
            )
        }

        return if (MarkAccessibilityService.takeScreenshot()) {
            ToolResult.Success("Screenshot captured.")
        } else {
            ToolResult.Failure("Failed to capture screenshot.", reason = "system_error")
        }
    }
}
