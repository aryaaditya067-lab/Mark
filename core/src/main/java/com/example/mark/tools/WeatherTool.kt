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
import com.example.mark.utils.ForecastSummary
import kotlin.math.roundToInt

class WeatherTool(
    private val location: LocationProvider,
    private val weather: WeatherRepository = WeatherRepository.instance
) : Tool {

    override val name = "get_weather"

    override val intent = IntentType.GET_WEATHER

    override val definition = FunctionDef(
        name = name,
        description = "Get the weather now, or the forecast for today or tomorrow. If the user names a " +
            "city, pass it; otherwise the device's own location is used.",
        parameters = Parameters(
            properties = mapOf(
                "city" to Property(
                    "string",
                    "City name, e.g. 'Chennai'. Omit to use the device's location."
                ),
                "when" to Property("string", "now (default), today or tomorrow")
            )
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val city = request.string("city")
        val whenAsked = request.string("when")?.lowercase() ?: "now"

        val coords = if (city == null) {
            if (!location.hasPermission()) {
                return ToolResult.Failure(
                    "Location permission has not been granted.",
                    reason = "no_permission"
                )
            }
            location.currentCoords()
                ?: return ToolResult.Failure(
                    "Could not determine the device's location.",
                    reason = "no_location"
                )
        } else null

        if (whenAsked == "today" || whenAsked == "tomorrow") {
            val forecast = if (city != null) weather.forecastByCity(city) else weather.forecastByCoords(coords!!.first, coords.second)
            return forecast.fold(
                onSuccess = { f ->
                    val offset = f.city?.timezone ?: 0
                    val today = java.time.Instant.now().atOffset(java.time.ZoneOffset.ofTotalSeconds(offset)).toLocalDate()
                    val day = if (whenAsked == "tomorrow") today.plusDays(1) else today
                    ForecastSummary.forDay(f.list.orEmpty(), day, offset, f.city?.name ?: city ?: "your location")
                        ?.let { ToolResult.Success("$it (${whenAsked})") }
                        ?: ToolResult.Failure("No forecast available for $whenAsked.", reason = "no_data")
                },
                onFailure = { ToolResult.Failure("Forecast lookup failed: ${it.message ?: "unknown error"}", reason = "network_error") }
            )
        }

        val result = if (city != null) weather.byCity(city) else weather.byCoords(coords!!.first, coords.second)

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
