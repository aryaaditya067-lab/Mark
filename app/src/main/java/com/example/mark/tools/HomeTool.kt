package com.example.mark.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.router.IntentType
import com.example.mark.service.MarkAccessibilityService

class HomeTool(private val context: Context) : Tool {

    override val name = "go_home"
    override val intent = IntentType.GO_HOME

    override val definition = FunctionDef(
        name = name,
        description = "Go to the home screen (close the current app).",
        parameters = Parameters(properties = emptyMap())
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (!MarkAccessibilityService.isEnabled) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return ToolResult.Failure(
                "Enable Mark's Accessibility Service in phone settings to use this.",
                reason = "no_permission"
            )
        }

        return if (MarkAccessibilityService.goHome()) {
            ToolResult.Success("Going home.")
        } else {
            ToolResult.Failure("Couldn't go home.", reason = "action_failed")
        }
    }
}
