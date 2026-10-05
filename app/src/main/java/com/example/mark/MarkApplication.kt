package com.example.mark

import android.app.Application
import com.example.mark.assistant.MarkAssistant
import com.example.mark.health.HealthConnectProvider
import com.example.mark.repository.SettingsRepository
import com.example.mark.repository.ContactRepository
import com.example.mark.tool.*
import com.example.mark.reminder.AndroidReminders
import com.example.mark.tools.CancelReminderTool
import com.example.mark.tools.HomeTool
import com.example.mark.tools.ListRemindersTool
import com.example.mark.tools.SetReminderTool
import com.example.mark.tools.SleepTool
import com.example.mark.tools.StepsTool
import com.example.mark.utils.SpeechRecognizerHelper
import com.example.mark.utils.TextToSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MarkApplication : Application() {

    lateinit var healthProvider: HealthConnectProvider
    lateinit var ttsManager: TextToSpeechManager
    lateinit var speechHelper: SpeechRecognizerHelper
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        settingsRepository = SettingsRepository(this)
        healthProvider = HealthConnectProvider(this)
        ttsManager = TextToSpeechManager(this)
        speechHelper = SpeechRecognizerHelper(this)

        // Warm up ContactRepository and OpenAppTool on Dispatchers.IO
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ContactRepository.getInstance(applicationContext).tryRefresh()
            // OpenAppTool is a singleton accessed via MarkAssistant, 
            // and it pre-warms its cache in its init block.
            // Just calling MarkAssistant.get() will trigger it.
        }

        // Initialize the shared AssistantController with phone-specific tools
        val reminders = AndroidReminders(this)
        MarkAssistant.get(
            this,
            extraTools = listOf(
                StepsTool(this, healthProvider),
                SleepTool(healthProvider),
                HomeTool(this),
                ReadNotificationsTool(this),
                ReadLastMessageTool(this),
                CheckNewMessagesTool(this),
                UnreadCountTool(this),
                NavigateToTool(this),
                GetDistanceTool(this),
                FindNearbyTool(this),
                ScreenshotTool(this),
                CallTool(this),
                CallExecuteTool(this),
                SmsTool(this),
                SmsExecuteTool(this),
                SetReminderTool(reminders),
                ListRemindersTool(reminders),
                CancelReminderTool(reminders)
            )
        )
    }
}
