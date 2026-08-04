package com.example.mark.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenWeatherMap current-weather endpoint.
 * Docs: openweathermap.org/current
 */
interface WeatherApiService {

    @GET("data/2.5/weather")
    suspend fun byCoords(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse

    @GET("data/2.5/weather")
    suspend fun byCity(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse
}

data class WeatherResponse(
    val name: String?,                       // city name
    val weather: List<WeatherDescription>?,
    val main: MainWeather?,
    val wind: Wind?
)

data class WeatherDescription(
    val main: String?,                       // "Rain"
    val description: String?                 // "light rain"
)

data class MainWeather(
    val temp: Double?,
    val feels_like: Double?,
    val humidity: Int?
)

data class Wind(
    val speed: Double?                       // m/s
)
