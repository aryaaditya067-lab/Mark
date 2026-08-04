package com.example.mark.tools

import com.example.mark.assistant.LocationProvider
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.network.WeatherResponse
import com.example.mark.repository.WeatherRepository
import com.example.mark.router.IntentType
import kotlin.math.roundToInt

class WeatherTool(
    private val location: LocationProvider,
    private val weather: WeatherRepository = WeatherRepository.instance
) : Tool {

    override val name = "get_weather"

    override val intent = IntentType.GET_WEATHER

    override val definition = FunctionDef(
        name = name,
        description = "Get the current weather. If the user names a city, pass it. " +
                "If they ask about the weather without naming a place, omit the city " +
                "and the device's own location will be used.",
        parameters = Parameters(
            properties = mapOf(
                "city" to Property(
                    "string",
                    "City name, e.g. 'Chennai'. Omit to use the device's location."
                )
            )
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val city = request.string("city")

        val result = if (city != null) {
            weather.byCity(city)
        } else {
            if (!location.hasPermission()) {
                return ToolResult.Failure(
                    "Location permission has not been granted.",
                    reason = "no_permission"
                )
            }
            val coords = location.currentCoords()
                ?: return ToolResult.Failure(
                    "Could not determine the device's location.",
                    reason = "no_location"
                )
            weather.byCoords(coords.first, coords.second)
        }

        return result.fold(
            onSuccess = { ToolResult.Success(it.describe()) },
            onFailure = { ToolResult.Failure("Weather lookup failed: ${it.message ?: "unknown error"}", reason = "network_error") }
        )
    }

    private fun WeatherResponse.describe(): String {
        val place = name ?: "your location"
        val desc = weather?.firstOrNull()?.description ?: "unknown conditions"
        val temp = main?.temp?.roundToInt()
        val feels = main?.feels_like?.roundToInt()
        val humidity = main?.humidity

        return buildString {
            append("Weather in $place: $desc")
            temp?.let { append(", $it°C") }
            feels?.let { if (it != temp) append(" (feels like $it°C)") }
            humidity?.let { append(", humidity $it%") }
            append(".")
        }
    }
}
