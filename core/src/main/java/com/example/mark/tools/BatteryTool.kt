package com.example.mark.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.router.IntentType

class BatteryTool(private val context: Context) : Tool {

    override val name = "battery_info"

    override val intent = IntentType.GET_BATTERY

    override val definition = FunctionDef(
        name = name,
        description = "Check the device battery level and charging status.",
        parameters = Parameters(properties = emptyMap())
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        
        val pct = (level * 100 / scale.toFloat()).toInt()
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                         status == BatteryManager.BATTERY_STATUS_FULL

        val text = "Battery is at $pct%${if (isCharging) " and charging" else ""}."
        
        return ToolResult.Success(
            text = text,
            data = mapOf(
                "percentage" to pct.toString(),
                "is_charging" to isCharging.toString()
            )
        )
    }
}
