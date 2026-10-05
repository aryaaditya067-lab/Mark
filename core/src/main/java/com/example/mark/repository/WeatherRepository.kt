package com.example.mark.repository

import com.example.mark.network.RetrofitClient
import com.example.mark.network.WeatherApiService
import com.example.mark.network.ForecastResponse
import com.example.mark.network.WeatherResponse
import com.example.mark.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherRepository(
    private val api: WeatherApiService = RetrofitClient.weatherApi
) {

    suspend fun byCoords(lat: Double, lon: Double): Result<WeatherResponse> =
        withContext(Dispatchers.IO) {
            runCatching { api.byCoords(lat, lon, Constants.WEATHER_API_KEY) }
        }

    suspend fun forecastByCoords(lat: Double, lon: Double): Result<ForecastResponse> =
        withContext(Dispatchers.IO) {
            runCatching { api.forecastByCoords(lat, lon, Constants.WEATHER_API_KEY) }
        }

    suspend fun forecastByCity(city: String): Result<ForecastResponse> =
        withContext(Dispatchers.IO) {
            runCatching { api.forecastByCity(city, Constants.WEATHER_API_KEY) }
        }

    suspend fun byCity(city: String): Result<WeatherResponse> =
        withContext(Dispatchers.IO) {
            runCatching { api.byCity(city, Constants.WEATHER_API_KEY) }
        }

    companion object {
        val instance: WeatherRepository by lazy { WeatherRepository() }
    }
}
