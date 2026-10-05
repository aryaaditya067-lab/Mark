package com.example.mark.tools

import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.model.Reminder
import com.example.mark.repository.Reminders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderToolsTest {

    private class FakeReminders : Reminders {
        val all = mutableListOf<Reminder>()
        override suspend fun add(reminder: Reminder) { all += reminder }
        override suspend fun list() = all.toList()
        override suspend fun cancel(ids: Set<String>) { all.removeAll { it.id in ids } }
    }

    private val now = ZonedDateTime.of(2026, 10, 5, 17, 30, 0, 0, ZoneId.of("Asia/Kolkata"))
    private val store = FakeReminders()
    private val set = SetReminderTool(store) { now }
    private val list = ListRemindersTool(store) { now }
    private val cancel = CancelReminderTool(store)

    @Test
    fun setsAndDescribes() = runBlocking {
        val result = set.execute(ToolRequest.of(mapOf("text" to "Take your medicine", "at" to "2026-10-05T18:00", "repeat" to "daily")))
        assertEquals("Reminder set for today at 6:00 PM, every day: Take your medicine", result.text)
        assertEquals("daily", store.all.single().repeat)
        assertEquals(now.withHour(18).withMinute(0).toInstant().toEpochMilli(), store.all.single().at)
    }

    @Test
    fun acceptsMinutesEvenAsDecimal() = runBlocking {
        set.execute(ToolRequest.of(mapOf("text" to "Tea", "in_minutes" to "20.0")))
        assertEquals(now.plusMinutes(20).toInstant().toEpochMilli(), store.all.single().at)
    }

    @Test
    fun asksWhenTimeIsMissingOrPast() = runBlocking {
        assertTrue(set.execute(ToolRequest.of(mapOf("text" to "Tea"))) is ToolResult.Failure)
        assertTrue(set.execute(ToolRequest.of(mapOf("text" to "Tea", "at" to "2026-10-05T09:00"))) is ToolResult.Failure)
        assertTrue(store.all.isEmpty())
    }

    @Test
    fun listsAndCancels() = runBlocking {
        set.execute(ToolRequest.of(mapOf("text" to "Call mom", "at" to "2026-10-06T09:00")))
        set.execute(ToolRequest.of(mapOf("text" to "Take medicine", "at" to "2026-10-05T18:00")))
        assertEquals("Take medicine, today at 6:00 PM; Call mom, tomorrow at 9:00 AM", list.execute(ToolRequest.of()).text)
        assertTrue(cancel.execute(ToolRequest.of(mapOf("query" to "medicine"))) is ToolResult.Success)
        assertEquals(listOf("Call mom"), store.all.map { it.text })
        assertTrue(cancel.execute(ToolRequest.of(mapOf("query" to "gym"))) is ToolResult.Failure)
    }
}
