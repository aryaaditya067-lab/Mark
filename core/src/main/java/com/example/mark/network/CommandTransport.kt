package com.example.mark.network

import android.content.Context
import com.example.mark.model.Command
import com.example.mark.model.CommandResult
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import java.nio.charset.StandardCharsets

/**
 * Handles sending and receiving commands over the Wear OS Data Layer.
 */
class CommandTransport(context: Context) : MessageClient.OnMessageReceivedListener {
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val gson = Gson()

    private val _results = MutableSharedFlow<CommandResult>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val results: SharedFlow<CommandResult> = _results.asSharedFlow()

    init {
        messageClient.addListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == RESULT_PATH) {
            val json = String(messageEvent.data, StandardCharsets.UTF_8)
            try {
                val result = gson.fromJson(json, CommandResult::class.java)
                _results.tryEmit(result)
            } catch (e: Exception) {
                android.util.Log.e("CommandTransport", "Failed to parse result", e)
            }
        }
    }

    fun release() {
        messageClient.removeListener(this)
    }

    companion object {
        const val COMMAND_PATH = "/command"
        const val RESULT_PATH = "/command_result"
    }

    suspend fun sendCommand(command: Command): Boolean {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val payload = gson.toJson(command).toByteArray(StandardCharsets.UTF_8)
            
            var sent = false
            for (node in nodes) {
                messageClient.sendMessage(node.id, COMMAND_PATH, payload).await()
                sent = true
            }
            sent
        } catch (e: Exception) {
            android.util.Log.e("CommandTransport", "Failed to send command", e)
            false
        }
    }

    suspend fun sendResult(nodeId: String, result: CommandResult) {
        try {
            val payload = gson.toJson(result).toByteArray(StandardCharsets.UTF_8)
            messageClient.sendMessage(nodeId, RESULT_PATH, payload).await()
        } catch (e: Exception) {
            android.util.Log.e("CommandTransport", "Failed to send result", e)
        }
    }
}
