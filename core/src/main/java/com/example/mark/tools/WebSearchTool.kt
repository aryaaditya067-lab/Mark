package com.example.mark.tools

import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.network.TavilyApiService
import com.example.mark.network.TavilyRequest
import com.example.mark.network.TavilyResponse
import kotlinx.coroutines.CancellationException

/**
 * Current information from the web: news, scores, prices, anything after the
 * model's training. LLM-only. Registered only when a Tavily key is set.
 */
class WebSearchTool(private val api: TavilyApiService, private val apiKey: String) : Tool {

    override val name = "web_search"

    override val definition = FunctionDef(
        name = name,
        description = "Search the web for anything current or factual you are not sure of: news, scores, " +
            "prices, opening hours, recent events. Returns a short answer and sources.",
        parameters = Parameters(
            properties = mapOf(
                "query" to Property("string", "What to search for, as a short web query"),
                "topic" to Property("string", "general, news or finance")
            ),
            required = listOf("query")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val query = request.string("query") ?: return ToolResult.Failure("What should I search for?", reason = "missing_arg")
        val topic = request.string("topic")?.lowercase()?.takeIf { it in TOPICS } ?: "general"
        val response = try {
            api.search("Bearer $apiKey", TavilyRequest(query = query, topic = topic))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ToolResult.Failure("The web search failed: ${e.message ?: "network error"}", reason = "network_error")
        }
        return format(response)?.let { ToolResult.Success(it) }
            ?: ToolResult.Failure("The web search found nothing useful.", reason = "no_results")
    }

    companion object {
        private val TOPICS = setOf("general", "news", "finance")
        private const val MAX_CHARS = 1500

        /**
         * Answer first, then up to three sources by site name. Web text is
         * untrusted input to the model; actions with consequences are gated
         * by confirmation in code, whatever a page says.
         */
        fun format(response: TavilyResponse): String? {
            val parts = mutableListOf<String>()
            response.answer?.takeIf { it.isNotBlank() }?.let { parts += "Answer: ${it.trim()}" }
            response.results.orEmpty().take(3).forEach { r ->
                val site = r.url?.let { siteName(it) } ?: return@forEach
                val snippet = r.content?.trim()?.take(300).orEmpty()
                parts += "Source ($site): ${r.title?.trim().orEmpty()} - $snippet"
            }
            return parts.joinToString("\n").take(MAX_CHARS).ifBlank { null }
        }

        fun siteName(url: String): String? =
            Regex("""^https?://(?:www\.)?([^/:?#]+)""").find(url.trim())?.groupValues?.get(1)
    }
}
