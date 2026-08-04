package com.example.mark.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.health.HeartRateReader
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.router.IntentType

class HeartRateTool(private val context: Context) : Tool {

    private val reader = HeartRateReader(context)

    override val name = "get_heart_rate"

    override val intent = IntentType.GET_HEART_RATE

    override val definition = FunctionDef(
        name = name,
        description = "Get the user's current heart rate from the watch sensor.",
        parameters = Parameters(properties = emptyMap())
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            return ToolResult.Failure(
                "Sensor permission not granted.",
                reason = "no_permission"
            )
        }

        val bpm = reader.readOnce()
            ?: return ToolResult.Failure("Could not read heart rate. Is the watch on your wrist?", reason = "no_data")

        val roundedBpm = bpm.toInt()
        return ToolResult.Success(
            text = "Your heart rate is $roundedBpm BPM.",
            data = mapOf("bpm" to roundedBpm.toString())
        )
    }
}
