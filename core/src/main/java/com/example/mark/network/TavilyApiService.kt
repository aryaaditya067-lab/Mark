package com.example.mark.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/** Tavily web search (api.tavily.com), used by the web_search tool. */
interface TavilyApiService {
    @POST("search")
    suspend fun search(
        @Header("Authorization") authHeader: String,
        @Body request: TavilyRequest
    ): TavilyResponse
}

data class TavilyRequest(
    val query: String,
    val topic: String = "general",              // general | news | finance
    @SerializedName("include_answer") val includeAnswer: Boolean = true,
    @SerializedName("max_results") val maxResults: Int = 3,
    @SerializedName("search_depth") val searchDepth: String = "basic"
)

data class TavilyResponse(
    val answer: String?,
    val results: List<TavilyResult>?
)

data class TavilyResult(
    val title: String?,
    val url: String?,
    val content: String?
)
