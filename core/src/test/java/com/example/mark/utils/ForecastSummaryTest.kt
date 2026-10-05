package com.example.mark.utils

import com.example.mark.network.ForecastEntry
import com.example.mark.network.MainWeather
import com.example.mark.network.WeatherDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ForecastSummaryTest {

    private val ist = 19800 // +05:30

    private fun entry(day: Int, hour: Int, temp: Double, desc: String, pop: Double) = ForecastEntry(
        dt = LocalDate.of(2026, 10, day).atTime(hour, 0).toEpochSecond(ZoneOffset.ofTotalSeconds(ist)),
        main = MainWeather(temp = temp, feels_like = null, humidity = null),
        weather = listOf(WeatherDescription(main = null, description = desc)),
        pop = pop
    )

    @Test
    fun summarisesOneDay() {
        val entries = listOf(
            entry(5, 21, 27.0, "clear sky", 0.0),
            entry(6, 6, 24.4, "light rain", 0.6),
            entry(6, 12, 31.6, "light rain", 0.8),
            entry(6, 18, 28.0, "broken clouds", 0.2),
        )
        assertEquals(
            "Forecast for Delhi: light rain, 24 to 32°C, 80% chance of rain.",
            ForecastSummary.forDay(entries, LocalDate.of(2026, 10, 6), ist, "Delhi")
        )
    }

    @Test
    fun noEntriesForThatDay() {
        assertNull(ForecastSummary.forDay(listOf(entry(5, 21, 27.0, "clear sky", 0.0)), LocalDate.of(2026, 10, 7), ist, "Delhi"))
    }
}
