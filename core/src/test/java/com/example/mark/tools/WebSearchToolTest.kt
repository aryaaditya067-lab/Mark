package com.example.mark.tools

import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.TavilyApiService
import com.example.mark.network.TavilyRequest
import com.example.mark.network.TavilyResponse
import com.example.mark.network.TavilyResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolTest {

    @Test
    fun formatsAnswerAndSourcesBySiteName() {
        val text = WebSearchTool.format(TavilyResponse(
            answer = "India won by 5 wickets.",
            results = listOf(TavilyResult("Match report", "https://www.espncricinfo.com/x/y", "India chased 250..."))
        ))!!
        assertTrue(text, text.startsWith("Answer: India won by 5 wickets."))
        assertTrue(text, text.contains("Source (espncricinfo.com): Match report - India chased 250..."))
        assertTrue("never the full URL", !text.contains("https://"))
    }

    @Test
    fun emptyResponseIsNothing() {
        assertNull(WebSearchTool.format(TavilyResponse(answer = " ", results = emptyList())))
    }

    @Test
    fun sendsBearerKeyAndTopic() = runBlocking {
        var seen: Pair<String, TavilyRequest>? = null
        val api = object : TavilyApiService {
            override suspend fun search(authHeader: String, request: TavilyRequest): TavilyResponse {
                seen = authHeader to request
                return TavilyResponse("Sunny.", emptyList())
            }
        }
        val result = WebSearchTool(api, "tvly-key").execute(ToolRequest.of(mapOf("query" to "weather", "topic" to "NEWS")))
        assertEquals("Answer: Sunny.", result.text)
        assertEquals("Bearer tvly-key", seen!!.first)
        assertEquals("news", seen!!.second.topic)
    }

    @Test
    fun networkErrorIsAFailureNotACrash() = runBlocking {
        val api = object : TavilyApiService {
            override suspend fun search(authHeader: String, request: TavilyRequest): TavilyResponse = throw java.io.IOException("offline")
        }
        assertTrue(WebSearchTool(api, "k").execute(ToolRequest.of(mapOf("query" to "x"))) is ToolResult.Failure)
    }
}
