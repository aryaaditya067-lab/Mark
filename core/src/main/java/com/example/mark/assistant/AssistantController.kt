package com.example.mark.assistant

import com.example.mark.model.Command
import com.example.mark.model.CommandResult
import com.example.mark.model.Message
import com.example.mark.network.*
import com.example.mark.repository.ChatHistoryRepository
import com.example.mark.router.IntentRouter
import com.example.mark.router.IntentType
import com.example.mark.router.ReplyMode
import com.example.mark.router.RoutingDecision
import com.example.mark.router.replyMode
import com.example.mark.utils.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.lang.reflect.Type
import java.util.UUID

/**
 * The central assistant engine.
 */
class AssistantController(
    private val toolManager: ToolManager,
    private val router: IntentRouter = IntentRouter(),
    private val api: GroqApiService = RetrofitClient.groqApi,
    private val historyProvider: () -> ChatHistoryRepository? = { ChatHistoryRepository.instance },
    private val isWatch: Boolean = false,
    private val transport: CommandTransport? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val gson = Gson()
    private val toolCallListType = object : TypeToken<List<ToolCall>>() {}.type

    private val watchSessionHistory = mutableListOf<Message>()
    private var currentTurn: Job? = null
    private var lastSpokenReply: String? = null

    private data class AwaitingConfirmation(
        val intent: com.example.mark.router.Intent,
        val expiresAt: Long
    )
    private var awaitingConfirmation: AwaitingConfirmation? = null

    private data class IntentContext(
        val intent: IntentType,
        val params: Map<String, String>,
        val timestamp: Long
    )
    private var lastIntentContext: IntentContext? = null

    private data class UndoInfo(
        val intent: IntentType,
        val params: Map<String, String>,
        val timestamp: Long
    )
    private var lastUndoInfo: UndoInfo? = null

    private companion object {
        private val CONVERSATIONAL_INTENTS = setOf(
            IntentType.GREETING,
            IntentType.END_SESSION,
            IntentType.EASTER_EGG,
            IntentType.REPEAT,
            IntentType.CONFIRMATION,
            IntentType.UNDO,
            IntentType.HELP,
            IntentType.CONTEXT_FOLLOW_UP
        )

        private const val WATCH_HISTORY_CAP = 4 * Constants.MAX_HISTORY_MESSAGES

        // Intents that ALWAYS run on the phone when spoken from the watch.
        private val ALWAYS_REMOTE = setOf(
            IntentType.TOGGLE_FLASHLIGHT, IntentType.SET_DND, IntentType.GET_CALENDAR, IntentType.RING_PHONE,
            IntentType.SET_ROTATE, IntentType.MEDIA_CONTROL, IntentType.READ_NOTIFICATIONS, IntentType.READ_LAST_MESSAGE,
            IntentType.CHECK_NEW_MESSAGES, IntentType.UNREAD_COUNT, IntentType.NAVIGATE_TO, IntentType.GET_DISTANCE,
            IntentType.FIND_NEARBY, IntentType.TAKE_SCREENSHOT, IntentType.CALL_CONTACT, IntentType.CALL_EXECUTE,
            IntentType.SEND_SMS, IntentType.SMS_EXECUTE, IntentType.GO_HOME, IntentType.LAPTOP_CONTROL
        )

        // Intents that run on the phone UNLESS the user said "watch pe ...".
        // NOTE: OPEN_APP must stay in this set — it has silently fallen out of
        // remote routing three times now. If "whatsapp kholo" ever opens the
        // watch app again, check here first.
        private val REMOTE_UNLESS_WATCH_TARGET = setOf(
            IntentType.SET_VOLUME, IntentType.SET_BRIGHTNESS, IntentType.SET_SILENT,
            IntentType.GET_BATTERY, IntentType.SET_ALARM, IntentType.OPEN_APP
        )
    }

    fun stop() {
        currentTurn?.cancel()
        currentTurn = null
    }

    suspend fun send(userText: String): Flow<AssistantEvent> = flow {
        stop()
        currentTurn = currentCoroutineContext()[Job]

        try {
            val initialDecision = router.route(userText)
            val isSms = initialDecision is RoutingDecision.Offline && initialDecision.intent.type == IntentType.SEND_SMS
            val segments = if (isSms) listOf(userText) else splitCompoundCommand(userText)

            val turnResults = mutableListOf<Triple<String, ReplyMode, Boolean>>()

            for (segment in segments) {
                val res = processTurn(segment)
                if (res != null) {
                    turnResults.add(res)
                    if (awaitingConfirmation != null) break
                } else {
                    break
                }
            }

            if (turnResults.isNotEmpty()) {
                if (turnResults.all { it.second == ReplyMode.SILENT_CONFIRM && it.third }) {
                    val text = if (turnResults.size > 1) "All done, sir." else turnResults.first().first
                    emit(AssistantEvent.Text(text, ReplyMode.SILENT_CONFIRM))
                } else {
                    val ordinals = listOf("First", "Second", "Third")
                    val combinedText = buildString {
                        if (turnResults.size > 1) {
                            turnResults.forEachIndexed { i, r ->
                                if (i > 0) append(" and ")
                                if (r.third) {
                                    if (r.second == ReplyMode.SPEAK) append(r.first)
                                    else append("${ordinals.getOrNull(i) ?: "Next"} one is done, sir.")
                                } else {
                                    append("${ordinals.getOrNull(i) ?: "Next"} one not understood, sir.")
                                }
                            }
                        } else {
                            append(turnResults.first().first)
                        }
                    }
                    emit(AssistantEvent.Text(combinedText.trim(), ReplyMode.SPEAK))
                }
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(AssistantEvent.Error(e))
        } finally {
            emit(AssistantEvent.Done)
            currentTurn = null
        }
    }

    private suspend fun FlowCollector<AssistantEvent>.processTurn(userText: String): Triple<String, ReplyMode, Boolean>? {
        val decision = router.route(userText)

        if (decision is RoutingDecision.Offline) {
            return performIntentExecution(decision.intent, userText)
        }

        // Online Path
        var groqReply = ""
        handleOnlineStream(userText).collect { if (it is AssistantEvent.Text) groqReply += it.content }
        return if (groqReply.isNotBlank()) Triple(groqReply, ReplyMode.SPEAK, true)
        else Triple(Persona.notUnderstood(), ReplyMode.SPEAK, false)
    }

    /**
     * Resolves a bare follow-up ("ab band kar", "aur badha") against whatever was
     * done last.
     *
     * This used to live in processTurn behind `decision == Online` — i.e. it only
     * ran when the resolver matched NOTHING. But "ab band kar" always matched
     * MEDIA_CONTROL through its generic "band" keyword, so the follow-up path was
     * unreachable and the phrase paused music instead of turning the torch off.
     * The resolver now emits CONTEXT_FOLLOW_UP for these, and it lands here.
     */
    private fun resolveFollowUp(ctx: String): com.example.mark.router.Intent? {
        val last = lastIntentContext ?: return null
        if (System.currentTimeMillis() - last.timestamp > 45000) return null

        val params = last.params.toMutableMap()
        when (ctx) {
            "off" -> params["state"] = "off"
            "on" -> params["state"] = "on"
            "up" -> { params["direction"] = "up"; params.remove("level") }
            "down" -> { params["direction"] = "down"; params.remove("level") }
            "again" -> { /* repeat the same action with the same params */ }
        }
        params.remove("negated")

        android.util.Log.d("MarkCtx", "follow-up '$ctx' -> ${last.intent} $params")
        return com.example.mark.router.Intent(last.intent, params)
    }

    private suspend fun FlowCollector<AssistantEvent>.performIntentExecution(intent: com.example.mark.router.Intent, userText: String): Triple<String, ReplyMode, Boolean>? {
        val now = System.currentTimeMillis()

        // NEGATION: resolver tagged this as "do NOT do it" ("torch mat chalana").
        // Acknowledge, never execute, never store context/undo for it.
        if (intent.params["negated"] == "true") {
            android.util.Log.d("MarkNeg", "suppressed ${intent.type}")
            return Triple("Alright sir, not doing it.", ReplyMode.SPEAK, true)
        }

        if (intent.type == IntentType.CONTEXT_FOLLOW_UP) {
            val resolved = resolveFollowUp(intent.params["ctx"] ?: "again")
                ?: return Triple("What are you referring to, sir?", ReplyMode.SPEAK, false)
            return performIntentExecution(resolved, userText)
        }

        if (intent.type == IntentType.REPEAT) {
            return Triple(lastSpokenReply ?: "I haven't said anything yet, sir.", ReplyMode.SPEAK, true)
        }

        if (intent.type == IntentType.UNDO) {
            val undo = lastUndoInfo
            if (undo != null && now - undo.timestamp < 45000) {
                lastUndoInfo = null
                val res = toolManager.execute(undo.intent, undo.params, userText)
                val reply = ResponseTemplates.render(undo.intent, res) ?: res.text
                return Triple(reply, undo.intent.replyMode(undo.params), res is ToolResult.Success)
            } else {
                return Triple("Nothing to undo, sir.", ReplyMode.SPEAK, false)
            }
        }

        // Remote Handling
        val isRemote = isWatch && (
                intent.type in ALWAYS_REMOTE ||
                        (intent.type in REMOTE_UNLESS_WATCH_TARGET && intent.params["target"] != "watch")
                )

        if (isRemote && transport != null) {
            val commandId = UUID.randomUUID().toString()
            val command = Command(id = commandId, type = intent.type, params = intent.params + ("raw_input" to userText))
            if (transport.sendCommand(command)) {
                val result = withTimeoutOrNull(5000) { transport.results.filter { it.commandId == commandId }.first() }
                if (result != null) {
                    if (intent.type == IntentType.CALL_CONTACT) {
                        handleCallResolution(result.data, result.text)
                        return null
                    }
                    if (intent.type == IntentType.SEND_SMS) {
                        handleSmsResolution(result.data, result.text)
                        return null
                    }
                    val mode = if (result.success) intent.type.replyMode(intent.params) else ReplyMode.SPEAK
                    if (mode == ReplyMode.SPEAK) lastSpokenReply = result.text

                    // Follow-ups ("ab band kar") resolve against the last intent.
                    // This used to be recorded only on the offline path, but on the
                    // watch almost everything worth following up on — the torch,
                    // brightness, volume — executes REMOTELY on the phone. So the
                    // context was never stored and every follow-up came back with
                    // "which one, sir?".
                    if (result.success) {
                        lastIntentContext = IntentContext(intent.type, intent.params, System.currentTimeMillis())
                    }
                    persist(listOf(Message(role = "user", content = userText), Message(role = "assistant", content = result.text, isToolReply = true)))
                    return Triple(result.text, mode, result.success)
                } else {
                    return Triple("Phone is taking too long to respond.", ReplyMode.SPEAK, false)
                }
            } else return Triple("Failed to reach your phone.", ReplyMode.SPEAK, false)
        } else if (intent.type in CONVERSATIONAL_INTENTS || toolManager.supports(intent.type)) {
            val (reply, mode) = handleOffline(userText, RoutingDecision.Offline(intent))
            return Triple(reply, mode, true)
        } else {
            android.util.Log.e("MarkRoute", "UNHANDLED ${intent.type}")
            return Triple("Sorry sir, my routing is misconfigured for this command.", ReplyMode.SPEAK, false)
        }
    }

    internal fun splitCompoundCommand(input: String): List<String> {
        val connectors = listOf(" aur ", " and ", " phir ", " uske baad ", " fir ")
        var currentSegments = listOf(input)
        for (conn in connectors) {
            val nextSegments = mutableListOf<String>()
            for (seg in currentSegments) {
                val parts = seg.split(conn)
                if (parts.size > 1 && parts.all { it.trim().split(Regex("\\s+")).size >= 2 }) {
                    nextSegments.addAll(parts.map { it.trim() })
                } else {
                    nextSegments.add(seg)
                }
            }
            currentSegments = nextSegments
            if (currentSegments.size >= 3) break
        }
        return currentSegments.take(3)
    }

    private suspend fun FlowCollector<AssistantEvent>.handleOffline(userText: String, decision: RoutingDecision.Offline): Pair<String, ReplyMode> {
        val intent = decision.intent

        if (intent.type == IntentType.CONFIRMATION) {
            val awaiting = awaitingConfirmation
            val now = System.currentTimeMillis()
            if (awaiting == null || now > awaiting.expiresAt) {
                android.util.Log.d("MarkConfirm", "state absent or expired")
                awaitingConfirmation = null
                return "For what, sir?" to ReplyMode.SPEAK
            }

            val decisionValue = intent.params["decision"]
            val target = awaiting.intent
            android.util.Log.d("MarkConfirm", "state present, decision=$decisionValue, target=${target.type}")
            awaitingConfirmation = null

            if (decisionValue == "yes") {
                val res = performIntentExecution(target, userText)
                return (res?.first ?: "Done sir.") to (res?.second ?: ReplyMode.SPEAK)
            } else {
                return "Alright, sir." to ReplyMode.SPEAK
            }
        }

        val result = when (intent.type) {
            IntentType.GREETING, IntentType.END_SESSION, IntentType.EASTER_EGG, IntentType.REPEAT, IntentType.HELP -> ToolResult.Success(intent.type.name, intent.params)
            else -> toolManager.execute(intent.type, intent.params, userText)
        }

        if (result is ToolResult.Success) {
            if (result.pendingIntent != null) awaitingConfirmation = AwaitingConfirmation(result.pendingIntent, System.currentTimeMillis() + 45000)
            lastIntentContext = IntentContext(intent.type, intent.params, System.currentTimeMillis())
            if (intent.type in setOf(IntentType.SET_BRIGHTNESS, IntentType.SET_VOLUME, IntentType.TOGGLE_FLASHLIGHT, IntentType.SET_DND, IntentType.SET_SILENT, IntentType.SET_ROTATE)) {
                result.undoParams?.let { lastUndoInfo = UndoInfo(intent.type, it, System.currentTimeMillis()) }
            }
        }

        val reply = ResponseTemplates.render(intent.type, result) ?: result.text
        val mode = if (result is ToolResult.Success) intent.type.replyMode(intent.params) else ReplyMode.SPEAK
        if (mode == ReplyMode.SPEAK) lastSpokenReply = reply
        persist(listOf(Message(role = "user", content = userText), Message(role = "assistant", content = reply, isToolReply = true)))
        return reply to mode
    }

    private suspend fun handleOnlineStream(userText: String): Flow<AssistantEvent> = flow {
        // Read context BEFORE storing this turn's user message — otherwise the
        // window already contains it and Groq sees the question twice.
        val past = if (isWatch) watchSessionHistory.toList()
        else runCatching { historyProvider()?.recent(Constants.MAX_HISTORY_MESSAGES) }.getOrNull() ?: emptyList()
        val stored = HistoryWindow.select(past, Constants.MAX_HISTORY_MESSAGES)
        persist(Message(role = "user", content = userText))
        val messages = buildList {
            add(GroqMessage(role = "system", content = PromptBuilder.systemPrompt()))
            addAll(stored.map { it.toGroqMessage() })
            add(GroqMessage(role = "user", content = userText))
        }.toMutableList()

        var fullReply = ""
        var hasToolCall = false
        askStream(messages).collect { chunk ->
            val choice = chunk.choices.firstOrNull() ?: return@collect
            if (choice.delta.toolCalls != null) hasToolCall = true
            choice.delta.content?.let { fullReply += it; emit(AssistantEvent.Text(it, ReplyMode.SPEAK)) }
        }

        if (fullReply.isNotBlank() && !hasToolCall) {
            lastSpokenReply = fullReply
            persist(Message(role = "assistant", content = fullReply))
            return@flow
        }

        val response = ask(messages)
        if (response.toolCalls.isNullOrEmpty()) {
            val text = response.content?.trim() ?: ""
            if (fullReply.isBlank() && text.isNotBlank()) { emit(AssistantEvent.Text(text, ReplyMode.SPEAK)); fullReply = text }
            if (fullReply.isNotBlank()) { lastSpokenReply = fullReply; persist(Message(role = "assistant", content = fullReply)) }
        } else {
            val calls = response.toolCalls
            val assistantMsg = Message(role = "assistant", content = response.content, toolCallsJson = gson.toJson(calls))
            persist(assistantMsg)
            messages.add(response)
            val toolResults = mutableListOf<Message>()
            for (call in calls) {
                val result = toolManager.execute(call.function.name, call.function.arguments, userText)
                val toolMsg = GroqMessage(role = "tool", toolCallId = call.id, name = call.function.name, content = result.text)
                messages.add(toolMsg)
                toolResults.add(Message(role = "tool", content = result.text, toolCallId = call.id, name = call.function.name))
            }
            persist(toolResults)
            var finalReply = ""
            askStream(messages).collect { chunk -> chunk.choices.firstOrNull()?.delta?.content?.let { finalReply += it; emit(AssistantEvent.Text(it, ReplyMode.SPEAK)) } }
            if (finalReply.isNotBlank()) { lastSpokenReply = finalReply; persist(Message(role = "assistant", content = finalReply)) }
        }
    }

    private suspend fun askStream(messages: List<GroqMessage>): Flow<GroqStreamResponse> = flow {
        val responseBody = api.chatCompletionStream(authHeader = groqAuthHeader(), request = GroqRequest(model = Constants.GROQ_MODEL, messages = messages, tools = toolManager.definitions, stream = true))
        responseBody.byteStream().bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") break
                    // Parse inside the try, emit outside it: emit() rethrows downstream
                    // failures and cancellation, which must not be swallowed here.
                    val chunk = try { gson.fromJson(data, GroqStreamResponse::class.java) } catch (e: Exception) { null }
                    if (chunk != null) emit(chunk)
                }
            }
        }
    }

    private fun groqAuthHeader(): String {
        check(Constants.GROQ_API_KEY.isNotBlank()) {
            "Groq API key is not set. Add GROQ_API_KEY to local.properties and rebuild."
        }
        return "Bearer ${Constants.GROQ_API_KEY}"
    }

    private suspend fun ask(messages: List<GroqMessage>): GroqMessage {
        val response = api.chatCompletion(authHeader = groqAuthHeader(), request = GroqRequest(model = Constants.GROQ_MODEL, messages = messages, tools = toolManager.definitions))
        return response.choices?.firstOrNull()?.message ?: error("Empty response")
    }

    private suspend fun persist(vararg messages: Message) = persist(messages.toList())
    private suspend fun persist(messages: List<Message>) {
        if (isWatch) {
            watchSessionHistory.addAll(messages)
            // Only the tail is ever sent to Groq; a long session must not grow forever.
            val overflow = watchSessionHistory.size - WATCH_HISTORY_CAP
            if (overflow > 0) watchSessionHistory.subList(0, overflow).clear()
        } else runCatching { historyProvider()?.appendAll(messages) }
    }
    private fun Message.toGroqMessage() = GroqMessage(role = role, content = content, toolCalls = toolCallsJson?.let { gson.fromJson(it, toolCallListType) }, toolCallId = toolCallId, name = name)

    suspend fun clearConversation() {
        stop()
        lastSpokenReply = null
        awaitingConfirmation = null
        lastIntentContext = null
        lastUndoInfo = null
        if (isWatch) watchSessionHistory.clear()
        else historyProvider()?.clear()
    }

    suspend fun executeCommand(command: Command): CommandResult {
        if (command.type == IntentType.CONFIRMATION && awaitingConfirmation != null) {
            val decision = command.params["decision"]
            val targetIntent = awaitingConfirmation!!.intent
            awaitingConfirmation = null
            if (decision == "yes") {
                val result = toolManager.execute(targetIntent.type, targetIntent.params, command.params["raw_input"])
                return CommandResult(commandId = command.id, text = result.text, success = result !is ToolResult.Failure, data = result.data)
            } else return CommandResult(command.id, "Okay, cancelled.", true)
        }
        val result = toolManager.execute(command.type, command.params, command.params["raw_input"])
        return CommandResult(commandId = command.id, text = result.text, success = result !is ToolResult.Failure, data = result.data)
    }

    private suspend fun FlowCollector<AssistantEvent>.handleCallResolution(data: Map<String, String>, rawText: String) {
        val status = data["status"]
        val name = data["name"] ?: ""
        val number = data["number"] ?: ""
        when (status) {
            "resolved" -> {
                emit(AssistantEvent.Text("Calling $name.", ReplyMode.SPEAK))
                val executeIntent = com.example.mark.router.Intent(IntentType.CALL_EXECUTE, mapOf("number" to number))
                delay(2000)
                val res = sendRemote(executeIntent.type, executeIntent.params)
                if (res is ToolResult.Success) emit(AssistantEvent.Text(res.text, ReplyMode.SPEAK))
            }
            "confirmation_needed" -> {
                emit(AssistantEvent.Text("Did you mean $name?", ReplyMode.SPEAK))
                awaitingConfirmation = AwaitingConfirmation(com.example.mark.router.Intent(IntentType.CALL_EXECUTE, mapOf("number" to number)), System.currentTimeMillis() + 45000)
            }
            "ambiguous" -> {
                val names = (data["candidates"] ?: "").replace("|", " or ")
                emit(AssistantEvent.Text("Did you mean $names?", ReplyMode.SPEAK))
            }
            else -> emit(AssistantEvent.Text(rawText, ReplyMode.SPEAK))
        }
    }

    private suspend fun FlowCollector<AssistantEvent>.handleSmsResolution(data: Map<String, String>, rawText: String) {
        val status = data["status"]
        val name = data["name"] ?: ""
        val number = data["number"] ?: ""
        val body = data["body"] ?: ""
        when (status) {
            "resolved" -> {
                emit(AssistantEvent.Text("Sending \"$body\" to $name. Confirm?", ReplyMode.SPEAK))
                awaitingConfirmation = AwaitingConfirmation(com.example.mark.router.Intent(IntentType.SMS_EXECUTE, mapOf("number" to number, "body" to body)), System.currentTimeMillis() + 45000)
            }
            "confirmation_needed" -> {
                emit(AssistantEvent.Text("Did you mean $name?", ReplyMode.SPEAK))
                awaitingConfirmation = AwaitingConfirmation(com.example.mark.router.Intent(IntentType.SMS_EXECUTE, mapOf("number" to number, "body" to body)), System.currentTimeMillis() + 45000)
            }
            "ambiguous" -> {
                val names = (data["candidates"] ?: "").replace("|", " or ")
                emit(AssistantEvent.Text("Did you mean $names?", ReplyMode.SPEAK))
            }
            else -> emit(AssistantEvent.Text(rawText, ReplyMode.SPEAK))
        }
    }

    private suspend fun sendRemote(type: IntentType, params: Map<String, String>): ToolResult {
        if (transport == null) return ToolResult.Failure("Transport not available", "hardware_error")
        val commandId = UUID.randomUUID().toString()
        val command = Command(id = commandId, type = type, params = params)
        if (transport.sendCommand(command)) {
            val result = withTimeoutOrNull(5000) { transport.results.filter { it.commandId == commandId }.first() }
            if (result != null) return ToolResult.Success(result.text, result.data)
        }
        return ToolResult.Failure("Phone timeout", "timeout")
    }
}