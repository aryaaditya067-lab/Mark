package com.example.mark.network

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * Retrofit service interface for the OpenAI-compatible chat API (Xiaomi MiMo).
 */
interface LlmApiService {

    /**
     * Sends a chat completion request to the LLM.
     *
     * @param authHeader The Bearer token for authentication.
     * @param request The chat completion request body.
     * @return The response containing the assistant's reply.
     */
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: LlmRequest
    ): LlmResponse

    @Streaming
    @POST("chat/completions")
    suspend fun chatCompletionStream(
        @Header("Authorization") authHeader: String,
        @Body request: LlmRequest
    ): ResponseBody
}
