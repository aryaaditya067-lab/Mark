package com.example.mark.assistant

import com.example.mark.router.Intent
import com.example.mark.router.IntentType
import com.example.mark.tools.LaptopCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationTextTest {

    @Test
    fun questionsComeFromTheRealParameters() {
        assertEquals("Shall I send \"I'm late\" to 98765 on WhatsApp? Say yes or no.",
            ConfirmationText.question(Intent(IntentType.SMS_EXECUTE, mapOf("number" to "98765", "body" to "I'm late", "app" to "whatsapp"))))
        assertEquals("Shall I call 98765? Say yes or no.",
            ConfirmationText.question(Intent(IntentType.CALL_EXECUTE, mapOf("number" to "98765"))))
        assertEquals("Shall I run clipboard set on the laptop (secret)? Say yes or no.",
            ConfirmationText.question(Intent(IntentType.LAPTOP_CONTROL, mapOf("action" to "clipboard_set", "text" to "secret"))))
        assertEquals("Shall I forget what I remembered about \"car keys\"? Say yes or no.",
            ConfirmationText.question(Intent(IntentType.FORGET_FACT, mapOf("query" to "car keys"))))
    }

    @Test
    fun laptopActionsThatChangeThingsNeedAYesFromTheModel() {
        listOf("type", "clipboard_set", "clipboard_get", "gradle", "shutdown", "wifi", "lock")
            .forEach { assertTrue(it, it in LaptopCommands.NEEDS_YES_FROM_LLM) }
        listOf("status", "open", "browser", "media", "brightness", "wake")
            .forEach { assertFalse(it, it in LaptopCommands.NEEDS_YES_FROM_LLM) }
    }
}
