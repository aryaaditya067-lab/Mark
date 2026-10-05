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
import com.example.mark.repository.MemoryStore
import com.example.mark.repository.SettingsRepository
import com.example.mark.repository.TaskRepository
import com.example.mark.utils.DeviceSituation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    @Volatile private var memoryStore: MemoryStore? = null
    @Volatile private var situationCache: CachedSituation? = null

    private fun isWatch(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

    /**
     * The one memory store of this process, shared by the assistant and the
     * Settings screen so both see the same facts. The watch never touches
     * Firestore, so its memory stays on the watch.
     */
    fun memory(context: Context): MemoryStore = memoryStore ?: synchronized(this) {
        memoryStore ?: (
            if (isWatch(context)) LocalMemoryStore(context.applicationContext) else FirestoreMemoryStore()
        ).also { memoryStore = it }
    }

    /** Call after changing something the situation snapshot shows, such as the user's name. */
    fun refreshSituation() {
        situationCache?.invalidate()
    }

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
        val memory = memory(appContext)
        val situation = CachedSituation(
            DeviceSituation(
                appContext,
                isWatch = isWatch,
                settings = SettingsRepository(appContext),
                tasks = if (isWatch) null else TaskRepository.instance
            ),
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ).also { situationCache = it }

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
            // On the watch this is only ever a schema: LAPTOP_CONTROL always runs
            // on the phone, so the call is forwarded rather than executed here.
            add(LaptopTool(appContext))
        } + extraTools

        // The watch's LLM can use every phone tool too; each call is carried to
        // the phone by intent, with the same spoken-yes gate.
        val allTools = if (isWatch) tools + PhoneToolSchemas.remoteTools(tools.map { it.name }.toSet()) else tools

        return AssistantController(
            toolManager = ToolManager(ToolRegistry(allTools)),
            isWatch = isWatch,
            transport = if (isWatch) CommandTransport(appContext) else null,
            memory = memory,
            situation = situation
        )
    }
}
