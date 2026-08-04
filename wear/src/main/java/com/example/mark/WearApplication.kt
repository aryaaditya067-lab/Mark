package com.example.mark

import android.app.Application
import com.example.mark.assistant.AssistantController
import com.example.mark.assistant.MarkAssistant
import com.example.mark.health.WatchHealthProvider
import com.example.mark.repository.SettingsRepository
import com.example.mark.tools.HeartRateTool
import com.example.mark.tools.SleepTool
import com.example.mark.tools.StepsTool
import com.example.mark.utils.SpeechRecognizerHelper
import com.example.mark.utils.TextToSpeechManager
import com.google.firebase.FirebaseApp
import com.example.mark.network.NetworkProvider
import com.example.mark.repository.WeatherRepository
import com.example.mark.utils.PlayLocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class WeatherCache(
    val tempCelsius: Int,
    val conditionShort: String,
    val timestamp: Long
)

class WearApplication : Application() {

    val healthProvider: WatchHealthProvider by lazy { WatchHealthProvider(this) }
    val ttsManager: TextToSpeechManager by lazy { TextToSpeechManager(this) }
    val speechHelper: SpeechRecognizerHelper by lazy { SpeechRecognizerHelper(this) }
    lateinit var settingsRepository: SettingsRepository

    var lastSessionEndTime: Long = 0
    var weatherCache: WeatherCache? = null

    val assistant: AssistantController by lazy {
        MarkAssistant.get(
            this,
            extraTools = listOf(
                HeartRateTool(this),
                StepsTool(this, healthProvider),
                SleepTool(healthProvider)
            )
        )
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        settingsRepository = SettingsRepository(this)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            NetworkProvider.start(applicationContext)

            // FIRESTORE WARMUP REMOVED.
            //
            // The watch keeps chat history in memory for the session only — it does
            // not read or write Firestore at all. Warming it up still paid the full
            // cost: gRPC channel setup, conscrypt registering hundreds of native
            // methods, protobuf class loading. On two A55 cores that competes with
            // the UI thread for the entire first few seconds after launch.
            //
            // If watch-side Firestore is ever needed again, warm it lazily at the
            // point of first use, never at startup.

            // Weather warm-up, bounded. Nothing in the UI may ever wait on this:
            // the greeting reads the cache if it is there and skips it if not.
            launch {
                runCatching {
                    val location = withTimeoutOrNull(3000) {
                        PlayLocationProvider(applicationContext).lastKnownCoords()
                    }
                    if (location != null) {
                        val weather = withTimeoutOrNull(4000) {
                            WeatherRepository.instance.byCoords(location.first, location.second).getOrNull()
                        }
                        if (weather != null) {
                            weatherCache = WeatherCache(
                                tempCelsius = weather.main?.temp?.toInt() ?: 0,
                                conditionShort = weather.weather?.firstOrNull()?.description ?: "",
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    }
                }
            }
        }
    }
}