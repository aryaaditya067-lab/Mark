package com.example.mark.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class BriefTextTest {

    @Test
    fun fullMorningBrief() {
        val text = BriefText.compose(
            hour = 7, name = "Aditya",
            weather = "Forecast for Delhi: light rain, 24 to 32°C, 80% chance of rain.",
            events = listOf(BriefText.Event("Standup", "10:00 AM"), BriefText.Event("Dentist", "5:30 PM")),
            reminders = listOf("Take medicine at 6:00 PM"),
            batteryPercent = 35
        )
        assertEquals(
            "Good morning, Aditya. Forecast for Delhi: light rain, 24 to 32°C, 80% chance of rain. " +
                "2 meetings: Standup at 10:00 AM, Dentist at 5:30 PM. One reminder: Take medicine at 6:00 PM. " +
                "Phone battery is at 35 percent.",
            text
        )
    }

    @Test
    fun quietDayWithoutOptionalParts() {
        assertEquals(
            "Good evening, sir. No meetings today.",
            BriefText.compose(hour = 20, name = "", weather = null, events = emptyList(), reminders = emptyList(), batteryPercent = 90)
        )
    }

    @Test
    fun longListsAreTrimmed() {
        val events = (1..6).map { BriefText.Event("M$it", "$it:00 PM") }
        val text = BriefText.compose(9, null, null, events, emptyList(), null)
        assertEquals("Good morning, sir. 6 meetings: M1 at 1:00 PM, M2 at 2:00 PM, M3 at 3:00 PM, M4 at 4:00 PM, and more.", text)
    }
}
