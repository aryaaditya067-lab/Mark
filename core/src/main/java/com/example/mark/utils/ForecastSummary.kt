package com.example.mark.utils

import com.example.mark.network.ForecastEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Turns OpenWeatherMap's 3-hourly forecast into one spoken-friendly line for a day. */
object ForecastSummary {

    /**
     * @param offsetSeconds the forecast city's UTC offset, so "tomorrow" means
     *   tomorrow there.
     * @return null when the forecast has no entries for [day].
     */
    fun forDay(entries: List<ForecastEntry>, day: LocalDate, offsetSeconds: Int, place: String): String? {
        val offset = ZoneOffset.ofTotalSeconds(offsetSeconds)
        val sameDay = entries.filter { e ->
            e.dt?.let { Instant.ofEpochSecond(it).atOffset(offset).toLocalDate() == day } == true
        }
        if (sameDay.isEmpty()) return null
        val temps = sameDay.mapNotNull { it.main?.temp }
        val low = temps.minOrNull()?.roundToInt()
        val high = temps.maxOrNull()?.roundToInt()
        val condition = sameDay.mapNotNull { it.weather?.firstOrNull()?.description }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "mixed conditions"
        val rain = sameDay.mapNotNull { it.pop }.maxOrNull()?.let { (it * 100).roundToInt() }
        return buildString {
            append("Forecast for $place: $condition")
            if (low != null && high != null) append(if (low == high) ", around $high°C" else ", $low to $high°C")
            if (rain != null) append(", $rain% chance of rain")
            append(".")
        }
    }
}
