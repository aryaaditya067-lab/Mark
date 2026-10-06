package com.example.mark.network

import com.example.mark.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    /**
     * Socket factory is resolved per connection, so a network that appears
     * after this client is built is still picked up.
     */
    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .socketFactory(DelegatingSocketFactory)
            .dns(NetworkProvider.dns())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Opens the connection to the LLM (DNS, TCP and TLS) before the first
     * question, so the question itself does not pay for it. Any answer will
     * do, even an error status: OkHttp keeps the connection for the next call.
     */
    fun warmUpLlm() {
        val request = okhttp3.Request.Builder().url(Constants.MIMO_BASE_URL + "models").head().build()
        okHttp.newCall(request).execute().close()
    }

    val llmApi: LlmApiService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.MIMO_BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LlmApiService::class.java)
    }

    val tavilyApi: TavilyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.tavily.com/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TavilyApiService::class.java)
    }

    val weatherApi: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.WEATHER_BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }
}
