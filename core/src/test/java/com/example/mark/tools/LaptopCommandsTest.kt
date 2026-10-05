package com.example.mark.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaptopCommandsTest {

    @Test
    fun extractsTheSearchOrTextFromACommand() {
        assertEquals("weather in delhi", LaptopCommands.payloadFrom("laptop pe google karo weather in delhi"))
        assertEquals("cats", LaptopCommands.payloadFrom("youtube pe search karo cats laptop pe"))
        assertEquals("hello world", LaptopCommands.payloadFrom("Mark, type karo hello world on the laptop"))
    }

    @Test
    fun nothingLeftMeansNoPayload() {
        assertNull(LaptopCommands.payloadFrom("laptop pe google karo"))
        assertNull(LaptopCommands.payloadFrom(null))
    }
}
