package com.example.mark.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LlmRequestTest {

    private fun json(request: LlmRequest) =
        JsonParser.parseString(Gson().toJson(request)).asJsonObject

    @Test
    fun disablesThinkingByDefault() {
        val body = json(LlmRequest(model = "m", messages = emptyList()))
        assertEquals("disabled", body.getAsJsonObject("thinking").get("type").asString)
    }

    @Test
    fun usesMaxCompletionTokens() {
        val body = json(LlmRequest(model = "m", messages = emptyList()))
        assertEquals(400, body.get("max_completion_tokens").asInt)
        assertFalse(body.has("max_tokens"))
    }

    @Test
    fun omitsUnsetOptionalFields() {
        val body = json(LlmRequest(model = "m", messages = emptyList()))
        assertFalse(body.has("tools"))
        assertFalse(body.has("tool_choice"))
    }
}
