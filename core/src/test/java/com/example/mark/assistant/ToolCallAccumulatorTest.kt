package com.example.mark.assistant

import com.example.mark.network.FunctionCallDelta
import com.example.mark.network.ToolCallDelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallAccumulatorTest {

    private fun d(index: Int?, id: String? = null, name: String? = null, args: String? = null) =
        ToolCallDelta(index, id, FunctionCallDelta(name, args))

    @Test
    fun assemblesArgumentsAcrossChunks() {
        val acc = ToolCallAccumulator()
        acc.add(listOf(d(0, id = "c1", name = "set_alarm", args = "")))
        acc.add(listOf(d(0, args = "{\"time\":")))
        acc.add(listOf(d(0, args = "\"07:00\"}")))
        val calls = acc.build()
        assertEquals(1, calls.size)
        assertEquals("c1", calls[0].id)
        assertEquals("set_alarm", calls[0].function.name)
        assertEquals("{\"time\":\"07:00\"}", calls[0].function.arguments)
    }

    @Test
    fun keepsParallelCallsInIndexOrder() {
        val acc = ToolCallAccumulator()
        acc.add(listOf(d(1, id = "b", name = "get_weather", args = "{}"), d(0, id = "a", name = "get_tasks")))
        val calls = acc.build()
        assertEquals(listOf("a", "b"), calls.map { it.id })
        assertEquals("{}", calls[0].function.arguments) // empty args default to {}
    }

    @Test
    fun repeatedNameIsNotDuplicated() {
        val acc = ToolCallAccumulator()
        acc.add(listOf(d(0, id = "a", name = "get_tasks")))
        acc.add(listOf(d(0, name = "get_tasks", args = "{}")))
        assertEquals("get_tasks", acc.build().single().function.name)
    }

    @Test
    fun missingIdAndIndexGetDefaults() {
        val acc = ToolCallAccumulator()
        acc.add(listOf(d(null, name = "get_tasks")))
        assertEquals("call_0", acc.build().single().id)
    }

    @Test
    fun ignoresNamelessFragmentsAndNull() {
        val acc = ToolCallAccumulator()
        acc.add(null)
        acc.add(listOf(d(0, args = "{}")))
        assertTrue(acc.isEmpty)
    }
}
