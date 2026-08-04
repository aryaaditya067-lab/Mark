package com.example.mark.service

import com.example.mark.assistant.MarkAssistant
import com.example.mark.model.Command
import com.example.mark.network.CommandTransport
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * Listens for commands from the watch and executes them on the phone.
 */
class MarkWearableListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private lateinit var transport: CommandTransport

    override fun onCreate() {
        super.onCreate()
        transport = CommandTransport(this)
        android.util.Log.d("MarkService", "Listener Service Created")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        android.util.Log.d("MarkService", "Message received on path: ${messageEvent.path}")
        if (messageEvent.path == CommandTransport.COMMAND_PATH) {
            val json = String(messageEvent.data, StandardCharsets.UTF_8)
            val command = try {
                gson.fromJson(json, Command::class.java)
            } catch (e: Exception) {
                android.util.Log.e("MarkService", "Failed to parse command", e)
                null
            }

            if (command != null) {
                android.util.Log.d("MarkRemote", "received=${command.type} params=${command.params}")
                android.util.Log.d("MarkService", "Executing command: ${command.type}")
                scope.launch {
                    try {
                        val assistant = MarkAssistant.get(this@MarkWearableListenerService)
                        val result = assistant.executeCommand(command)
                        android.util.Log.d("MarkService", "Command result: ${result.text}")
                        transport.sendResult(messageEvent.sourceNodeId, result)
                    } catch (e: Exception) {
                        android.util.Log.e("MarkService", "Execution failed", e)
                    }
                }
            }
        }
    }
}
