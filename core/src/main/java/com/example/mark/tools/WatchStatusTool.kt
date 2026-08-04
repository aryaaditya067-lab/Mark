package com.example.mark.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.StatFs
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.router.IntentType
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

class WatchStatusTool(private val context: Context) : Tool {

    override val name = "watch_status"
    override val intent = IntentType.WATCH_STATUS
    override val definition = FunctionDef(name, "Get system status of the watch.", Parameters(properties = emptyMap()))

    override suspend fun execute(request: ToolRequest): ToolResult {
        // Battery
        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = (level * 100 / scale.toFloat()).toInt()
        val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

        // Bluetooth / Connection
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.await() }.getOrNull()
        val isConnected = nodes?.isNotEmpty() ?: false

        // Storage
        val stat = StatFs(context.filesDir.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val availableGb = availableBytes / (1024 * 1024 * 1024f)

        val text = buildString {
            append("Battery $batteryPct percent")
            if (isCharging) append(" (charging)")
            append(", ")
            append(if (isConnected) "connected to phone" else "phone disconnected")
            append(", ")
            if (availableGb < 1.0) {
                append("storage low (${String.format("%.1f", availableGb)}GB free)")
            } else {
                append("all systems fine, sir")
            }
        }

        return ToolResult.Success(text, data = mapOf(
            "battery" to batteryPct.toString(),
            "charging" to isCharging.toString(),
            "connected" to isConnected.toString()
        ))
    }
}
