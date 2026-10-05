package com.example.mark.tools

import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.model.MemoryFact
import com.example.mark.repository.MemoryStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryToolsTest {

    private class InMemoryStore : MemoryStore {
        val facts = mutableListOf<MemoryFact>()
        override suspend fun all() = facts.toList()
        override suspend fun add(fact: MemoryFact) { facts += fact }
        override suspend fun remove(ids: Set<String>) { facts.removeAll { it.id in ids } }
    }

    private val store = InMemoryStore()
    private val remember = RememberFactTool(store)
    private val forget = ForgetFactTool(store)

    @Test
    fun remembersOnceAndNormalizes() = runBlocking {
        val first = remember.execute(ToolRequest.of(mapOf("fact" to "His car keys are in the top drawer.", "kind" to "PLACE")))
        val again = remember.execute(ToolRequest.of(mapOf("fact" to "his car keys are in the top drawer")))
        assertTrue(first is ToolResult.Success)
        assertTrue(again.text.startsWith("Already remembered"))
        assertEquals(1, store.facts.size)
        assertEquals("His car keys are in the top drawer", store.facts.single().text)
        assertEquals("place", store.facts.single().kind)
    }

    @Test
    fun unknownKindFallsBackToFact() = runBlocking {
        remember.execute(ToolRequest.of(mapOf("fact" to "Likes tea", "kind" to "nonsense")))
        assertEquals("fact", store.facts.single().kind)
    }

    @Test
    fun blankFactFails() = runBlocking {
        assertTrue(remember.execute(ToolRequest.of(mapOf("fact" to "  "))) is ToolResult.Failure)
    }

    @Test
    fun forgetsOnlyMatchingFacts() = runBlocking {
        remember.execute(ToolRequest.of(mapOf("fact" to "His car keys are in the top drawer")))
        remember.execute(ToolRequest.of(mapOf("fact" to "His car is a red Swift")))
        val result = forget.execute(ToolRequest.of(mapOf("query" to "car keys")))
        assertTrue(result is ToolResult.Success)
        assertEquals(listOf("His car is a red Swift"), store.facts.map { it.text })
        assertTrue(forget.execute(ToolRequest.of(mapOf("query" to "bike"))) is ToolResult.Failure)
    }
}
