package com.example.mark.assistant

import android.content.Context
import android.content.pm.PackageManager
import com.example.mark.network.CommandTransport
import com.example.mark.tools.AlarmTool
import com.example.mark.tools.AddTaskTool
import com.example.mark.tools.BatteryTool
import com.example.mark.tools.BrightnessTool
import com.example.mark.tools.CalendarTool
import com.example.mark.tools.CompleteTaskTool
import com.example.mark.tools.DndTool
import com.example.mark.tools.FlashlightTool
import com.example.mark.tools.GetTasksTool
import com.example.mark.tools.LaptopTool
import com.example.mark.tools.MediaTool
import com.example.mark.tools.OpenAppTool
import com.example.mark.tools.RingPhoneTool
import com.example.mark.tools.RotateLockTool
import com.example.mark.tools.SilentModeTool
import com.example.mark.tools.TimeTool
import com.example.mark.tools.VolumeTool
import com.example.mark.tools.WatchStatusTool
import com.example.mark.tools.WeatherTool
import com.example.mark.repository.FirestoreMemoryStore
import com.example.mark.repository.LocalMemoryStore
import com.example.mark.tools.ForgetFactTool
import com.example.mark.tools.RememberFactTool
import com.example.mark.utils.LazyLocationProvider
import com.example.mark.utils.PlayLocationProvider

/**
 * Wires up the assistant. Shared as a singleton within the process.
 *
 * Tools common to every device live here. Device-specific tools — the watch's
 * heart-rate sensor, for instance — are passed in by the module that owns them.
 */
object MarkAssistant {

    private var instance: AssistantController? = null

    /**
     * Gets the shared instance of the assistant, creating it if necessary.
     * Note: The [extraTools] list is only used if the instance has not yet
     * been created.
     */
    fun get(context: Context, extraTools: List<Tool> = emptyList()): AssistantController {
        return instance ?: synchronized(this) {
            instance ?: create(context, extraTools).also { instance = it }
        }
    }

    private fun create(context: Context, extraTools: List<Tool> = emptyList()): AssistantController {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val isWatch = pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
        // The watch never touches Firestore, so its memory stays on the watch.
        val memory = if (isWatch) LocalMemoryStore(appContext) else FirestoreMemoryStore()

        val tools = buildList {
            add(AddTaskTool())
            add(GetTasksTool())
            add(CompleteTaskTool())
            add(RememberFactTool(memory))
            add(ForgetFactTool(memory))
            add(AlarmTool(appContext))
            
            val location = if (isWatch) {
                LazyLocationProvider { PlayLocationProvider(appContext) }
            } else {
                PlayLocationProvider(appContext)
            }
            add(WeatherTool(location))

            add(CalendarTool(appContext))
            add(BatteryTool(appContext))
            add(FlashlightTool(appContext))
            add(VolumeTool(appContext))
            add(DndTool(appContext))
            add(RingPhoneTool(appContext))
            add(BrightnessTool(appContext))
            add(SilentModeTool(appContext))
            add(RotateLockTool(appContext))
            add(MediaTool(appContext))
            add(WatchStatusTool(appContext))
            val timeTool = TimeTool(appContext)
            add(timeTool)
            add(TimeTool.TimerQuery(timeTool))
            add(OpenAppTool(appContext))
            if (!isWatch) {
                add(LaptopTool(appContext))
            }
            
            // Only add tools added via extraTools to specific modules.
        } + extraTools

        return AssistantController(
            toolManager = ToolManager(ToolRegistry(tools)),
            isWatch = isWatch,
            transport = if (isWatch) CommandTransport(appContext) else null,
            memory = memory
        )
    }
}
