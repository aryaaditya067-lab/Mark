package com.example.mark.network

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * Retrofit service interface for the Groq API.
 */
interface GroqApiService {

    /**
     * Sends a chat completion request to the Groq API.
     *
     * @param authHeader The Bearer token for authentication.
     * @param request The chat completion request body.
     * @return The response containing the assistant's reply.
     */
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: GroqRequest
    ): GroqResponse

    @Streaming
    @POST("chat/completions")
    suspend fun chatCompletionStream(
        @Header("Authorization") authHeader: String,
        @Body request: GroqRequest
    ): ResponseBody
}
