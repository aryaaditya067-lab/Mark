package com.example.mark.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneToolSchemasTest {

    @Test
    fun namesAreUniqueAndMatchTheirSchemas() {
        val names = PhoneToolSchemas.all.map { it.definition.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun proxiesSkipToolsAlreadyOnTheDevice() {
        val proxies = PhoneToolSchemas.remoteTools(setOf("read_notifications"))
        assertFalse(proxies.any { it.name == "read_notifications" })
        assertEquals(PhoneToolSchemas.all.size - 1, proxies.size)
    }

    @Test
    fun onlyDialAndSendNeedASpokenYes() {
        val gated = PhoneToolSchemas.remoteTools(emptySet())
            .filter { it.needsConfirmation(ToolRequest.of()) }.map { it.name }.toSet()
        assertEquals(setOf("call_execute", "sms_execute"), gated)
    }

    @Test
    fun proxyNeverPretendsToSucceedLocally() = kotlinx.coroutines.runBlocking {
        val proxy = PhoneToolSchemas.remoteTools(emptySet()).first()
        assertTrue(proxy.execute(ToolRequest.of()) is ToolResult.Failure)
    }
}
