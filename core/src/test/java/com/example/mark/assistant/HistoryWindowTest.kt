package com.example.mark.assistant

import com.example.mark.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryWindowTest {

    private fun user(text: String) = Message(role = "user", content = text)
    private fun assistant(text: String) = Message(role = "assistant", content = text)
    private fun call(vararg ids: String) = Message(
        role = "assistant",
        toolCallsJson = ids.joinToString(",", "[", "]") {
            """{"id":"$it","type":"function","function":{"name":"get_tasks","arguments":"{}"}}"""
        }
    )
    private fun result(id: String) =
        Message(role = "tool", content = "ok", toolCallId = id, name = "get_tasks")

    @Test
    fun keepsPlainConversation() {
        val history = listOf(user("hi"), assistant("hello"), user("how are you"))
        assertEquals(history, HistoryWindow.select(history, 8))
    }

    @Test
    fun keepsCompleteToolExchange() {
        val history = listOf(user("tasks?"), call("a", "b"), result("a"), result("b"), assistant("two"))
        assertEquals(history, HistoryWindow.select(history, 8))
    }

    @Test
    fun dropsToolResultsWhoseCallFellOutsideTheWindow() {
        val history = listOf(user("tasks?"), call("a"), result("a"), assistant("one"), user("thanks"))
        val window = HistoryWindow.select(history, 3)
        assertEquals(listOf(assistant("one"), user("thanks")).map { it.content }, window.map { it.content })
    }

    @Test
    fun dropsToolCallWithMissingResults() {
        val ask = user("tasks?")
        val history = listOf(ask, call("a", "b"), result("a"))
        assertEquals(listOf(ask), HistoryWindow.select(history, 8))
    }

    @Test
    fun dropsDanglingToolCallAtEnd() {
        val ask = user("tasks?")
        val history = listOf(ask, call("a"))
        assertEquals(listOf(ask), HistoryWindow.select(history, 8))
    }

    @Test
    fun dropsToolCallWithUnreadableJson() {
        val ask = user("tasks?")
        val broken = Message(role = "assistant", toolCallsJson = "not json")
        assertEquals(listOf(ask), HistoryWindow.select(listOf(ask, broken, result("a")), 8))
    }

    @Test
    fun zeroLimitIsEmpty() {
        assertEquals(emptyList<Message>(), HistoryWindow.select(listOf(user("hi")), 0))
    }
}
