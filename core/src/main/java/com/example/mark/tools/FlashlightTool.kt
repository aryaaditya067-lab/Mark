package com.example.mark.tools

import android.content.Context
import android.hardware.camera2.CameraManager
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType

class FlashlightTool(private val context: Context) : Tool {

    override val name = "flashlight_manager"

    override val intent = IntentType.TOGGLE_FLASHLIGHT

    override val definition = FunctionDef(
        name = name,
        description = "Turn the flashlight on or off.",
        parameters = Parameters(
            properties = mapOf(
                "state" to Property("string", "Either 'on' or 'off'. If omitted, toggles current state.")
            )
        )
    )

    private companion object {
        var isTorchOn = false
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolResult.Failure("Camera service not available.", reason = "service_error")

        return try {
            val cameraId = manager.cameraIdList.firstOrNull() 
                ?: return ToolResult.Failure("No camera found.", reason = "no_hardware")
            
            val state = request.string("state")?.lowercase()
            val turnOn = when (state) {
                "on" -> true
                "off" -> false
                else -> !isTorchOn // Toggle if no state provided
            }

            val previousState = if (isTorchOn) "on" else "off"
            manager.setTorchMode(cameraId, turnOn)
            isTorchOn = turnOn
            ToolResult.Success(
                "Flashlight turned ${if (turnOn) "on" else "off"}.",
                undoParams = mapOf("state" to previousState)
            )
        } catch (e: Exception) {
            ToolResult.Failure("Flashlight error: ${e.message}", reason = "hardware_error")
        }
    }
}
