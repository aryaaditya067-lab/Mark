package com.example.mark.assistant

import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolManagerTest {

    private class FakeTool(
        override val name: String,
        private val body: suspend () -> ToolResult
    ) : Tool {
        override val definition = FunctionDef(name, "test", Parameters(properties = emptyMap()))
        override suspend fun execute(request: ToolRequest): ToolResult = body()
    }

    private fun manager(vararg tools: Tool) = ToolManager(ToolRegistry(tools.toList()))

    @Test
    fun throwingToolBecomesFailure() = runBlocking {
        val result = manager(FakeTool("boom") { error("kaput") }).execute("boom", "{}")
        assertTrue(result is ToolResult.Failure)
        assertEquals("Failed: kaput", result.text)
    }

    @Test
    fun toolsOwnTimeoutBecomesFailure() = runBlocking {
        val tool = FakeTool("slow") { withTimeout(1) { awaitCancellation() } }
        val result = manager(tool).execute("slow", "{}")
        assertTrue(result is ToolResult.Failure)
    }

    @Test
    fun cancellingTheTurnPropagates() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val tool = FakeTool("wait") { started.complete(Unit); awaitCancellation() }
        var continuedAfterCancel = false
        val turn = async {
            manager(tool).execute("wait", "{}")
            // Swallowing cancellation would let the turn carry on and reply.
            continuedAfterCancel = true
        }
        started.await()
        turn.cancel()
        turn.join()
        assertFalse(continuedAfterCancel)
    }

    @Test
    fun unknownToolIsFailure() = runBlocking {
        val result = manager().execute("nope", "{}")
        assertTrue(result is ToolResult.Failure)
    }
}
