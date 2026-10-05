package com.example.mark.assistant

import com.example.mark.network.FunctionDef
import com.example.mark.network.LlmApiService
import com.example.mark.network.LlmRequest
import com.example.mark.network.LlmResponse
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType
import com.google.gson.Gson
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the real LLM path against a fake server that replays scripted
 * streamed (SSE) responses, one per request.
 */
class AssistantControllerLlmTest {

    // ---- fake server ----

    private class FakeLlm(vararg rounds: List<String>) : LlmApiService {
        private val script = ArrayDeque(rounds.toList())
        val requests = mutableListOf<LlmRequest>()

        override suspend fun chatCompletion(authHeader: String, request: LlmRequest): LlmResponse =
            error("non-streaming path must not be used")

        override suspend fun chatCompletionStream(authHeader: String, request: LlmRequest): ResponseBody {
            requests += request
            val chunks = script.removeFirstOrNull() ?: error("unexpected request #${requests.size}")
            val sse = chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n"
            return sse.toResponseBody("text/event-stream".toMediaType())
        }
    }

    private val gson = Gson()

    private fun text(vararg parts: String) = parts.map {
        gson.toJson(mapOf("choices" to listOf(mapOf("delta" to mapOf("content" to it)))))
    }

    private fun toolCall(id: String, name: String, args: String) = listOf(
        gson.toJson(mapOf("choices" to listOf(mapOf("delta" to mapOf("tool_calls" to listOf(
            mapOf("index" to 0, "id" to id, "type" to "function", "function" to mapOf("name" to name, "arguments" to ""))
        )))))),
        gson.toJson(mapOf("choices" to listOf(mapOf("delta" to mapOf("tool_calls" to listOf(
            mapOf("index" to 0, "function" to mapOf("arguments" to args))
        )))))),
    )

    // ---- fake tools ----

    private class RecordingTool(
        override val name: String,
        override val intent: IntentType? = null,
        private val confirm: Boolean = false,
        private val result: (ToolRequest) -> ToolResult = { ToolResult.Success("ok") },
    ) : Tool {
        val calls = mutableListOf<ToolRequest>()
        override val definition = FunctionDef(name, "test", Parameters(properties = mapOf("x" to Property("string", "x"))))
        override suspend fun execute(request: ToolRequest): ToolResult { calls += request; return result(request) }
        override fun needsConfirmation(request: ToolRequest) = confirm
    }

    private fun controller(api: LlmApiService, vararg tools: Tool) = AssistantController(
        toolManager = ToolManager(ToolRegistry(tools.toList())),
        api = api,
        historyProvider = { null },
    )

    private fun AssistantController.ask(text: String) = runBlocking { send(text).toList() }

    private fun List<AssistantEvent>.texts() = filterIsInstance<AssistantEvent.Text>().map { it.content }

    // A free-form question the offline router does not handle.
    private val question = "explain why the sky looks blue to people"

    // ---- tests ----

    @Test
    fun streamsTextChunkByChunk() {
        val api = FakeLlm(text("Rayleigh ", "scattering, ", "sir."))
        val events = controller(api).ask(question)
        assertEquals(listOf("Rayleigh ", "scattering, ", "sir."), events.texts())
        assertEquals(AssistantEvent.Done, events.last())
    }

    @Test
    fun sendsTheQuestionOnce() {
        val api = FakeLlm(text("Hi."))
        controller(api).ask(question)
        assertEquals(1, api.requests.single().messages.count { it.role == "user" && it.content == question })
    }

    @Test
    fun chainsToolCallsAcrossRounds() {
        val tasks = RecordingTool("get_tasks", result = { ToolResult.Success("1. Gym at 7") })
        val alarm = RecordingTool("set_alarm")
        val api = FakeLlm(
            toolCall("c1", "get_tasks", "{}"),
            toolCall("c2", "set_alarm", "{\"x\":\"06:30\"}"),
            text("Alarm set for 6:30, sir."),
        )
        val events = controller(api, tasks, alarm).ask(question)

        assertEquals(1, tasks.calls.size)
        assertEquals("06:30", alarm.calls.single().string("x"))
        assertEquals(listOf("Alarm set for 6:30, sir."), events.texts())
        assertEquals(3, api.requests.size)
        // The third request carries both tool results back to the model.
        val toolMsgs = api.requests[2].messages.filter { it.role == "tool" }
        assertEquals(listOf("c1", "c2"), toolMsgs.map { it.toolCallId })
        assertEquals("1. Gym at 7", toolMsgs[0].content)
    }

    @Test
    fun lastRoundOffersNoTools() {
        val loop = RecordingTool("get_tasks")
        val rounds = (1..5).map { toolCall("c$it", "get_tasks", "{}") } + listOf(text("Done."))
        val api = FakeLlm(*rounds.toTypedArray())
        val events = controller(api, loop).ask(question)

        assertEquals(5, loop.calls.size)
        assertEquals(6, api.requests.size)
        assertTrue(api.requests.dropLast(1).all { it.tools != null })
        assertNull(api.requests.last().tools)
        assertEquals(listOf("Done."), events.texts())
    }

    @Test
    fun toolDataReachesTheModel() {
        val contact = RecordingTool("call_contact", result = {
            ToolResult.Success("resolved", mapOf("name" to "Rahul", "number" to "98765"))
        })
        val api = FakeLlm(toolCall("c1", "call_contact", "{}"), text("Found Rahul."))
        controller(api, contact).ask(question)
        val content = api.requests[1].messages.single { it.role == "tool" }.content!!
        assertTrue(content, content.contains("Rahul") && content.contains("98765"))
    }

    @Test
    fun consequentialCallWaitsForSpokenYes() {
        val sms = RecordingTool("sms_execute", intent = IntentType.SMS_EXECUTE, confirm = true,
            result = { ToolResult.Success("Message sent.") })
        val api = FakeLlm(
            toolCall("c1", "sms_execute", "{\"x\":\"hello\"}"),
            text("Send hello to Rahul?"),
        )
        val mark = controller(api, sms)

        assertEquals(listOf("Send hello to Rahul?"), mark.ask(question).texts())
        assertEquals("tool must not run before the user confirms", 0, sms.calls.size)
        val toolMsg = api.requests[1].messages.single { it.role == "tool" }.content!!
        assertTrue(toolMsg, toolMsg.contains("confirmation"))

        mark.ask("haan")
        assertEquals("hello", sms.calls.single().string("x"))
        assertEquals("the yes is handled offline, not sent to the model", 2, api.requests.size)
    }

    @Test
    fun bareYesWithNothingPendingGoesToTheModel() {
        val api = FakeLlm(text("Alright, sir."))
        val events = controller(api).ask("okay")
        assertEquals(1, api.requests.size)
        assertEquals(listOf("Alright, sir."), events.texts())
    }

    @Test
    fun spokenCallOnThePhoneActuallyDials() {
        val resolve = RecordingTool("call_contact", intent = IntentType.CALL_CONTACT, result = {
            ToolResult.Success("resolved", mapOf("status" to "resolved", "name" to "Rahul", "number" to "98765"))
        })
        val dial = RecordingTool("call_execute", intent = IntentType.CALL_EXECUTE, confirm = true)
        val api = FakeLlm()
        val events = controller(api, resolve, dial).ask("rahul ko call karo")

        assertEquals(listOf("Calling Rahul."), events.texts())
        assertEquals("98765", dial.calls.single().string("number"))
        assertEquals("offline command must not reach the model", 0, api.requests.size)
    }

    @Test
    fun emptyReplyFallsBackToNotUnderstood() {
        val api = FakeLlm(emptyList())
        val events = controller(api).ask(question)
        assertEquals(1, events.texts().size)
        assertTrue(events.texts().single().isNotBlank())
    }
}
