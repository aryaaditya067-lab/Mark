package com.example.mark.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceLabelsTest {
    @Test
    fun labelsLocalOnlineAndUnknownVoices() {
        assertEquals("British, male", VoiceLabels.label("en-gb-x-gbb-local"))
        assertEquals("British, male (online, more natural)", VoiceLabels.label("en-gb-x-gbb-network"))
        assertEquals("Australian, voice aua", VoiceLabels.label("en-au-x-aua-local"))
        assertEquals("fr-fr-x-frb-local", VoiceLabels.label("fr-fr-x-frb-local"))
    }
}
