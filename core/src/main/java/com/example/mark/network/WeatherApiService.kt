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

    /** 5-day forecast in 3-hour steps. */
    @GET("data/2.5/forecast")
    suspend fun forecastByCoords(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): ForecastResponse

    @GET("data/2.5/forecast")
    suspend fun forecastByCity(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): ForecastResponse

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

data class ForecastResponse(
    val city: ForecastCity?,
    val list: List<ForecastEntry>?
)

data class ForecastCity(
    val name: String?,
    val timezone: Int?                       // seconds east of UTC
)

data class ForecastEntry(
    val dt: Long?,                           // epoch seconds
    val main: MainWeather?,
    val weather: List<WeatherDescription>?,
    val pop: Double?                         // probability of precipitation, 0..1
)

data class Wind(
    val speed: Double?                       // m/s
)
