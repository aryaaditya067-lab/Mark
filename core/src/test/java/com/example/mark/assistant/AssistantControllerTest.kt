package com.example.mark.assistant

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class AssistantControllerTest {

    private lateinit var toolManager: ToolManager
    private lateinit var assistant: AssistantController

    @Before
    fun setup() {
        toolManager = mock()
        assistant = AssistantController(toolManager)
    }

    @Test
    fun testCompoundCommandSplitting() {
        val input = "light band karo aur music chalao and fan off kar do"
        val segments = assistant.splitCompoundCommand(input)
        
        assertEquals(3, segments.size)
        assertEquals("light band karo", segments[0])
        assertEquals("music chalao", segments[1])
        assertEquals("fan off kar do", segments[2])
    }

    @Test
    fun testCompoundCommandMaxSegments() {
        val input = "light on aur fan off aur sound badhao aur music chalao"
        val segments = assistant.splitCompoundCommand(input)
        
        // Should be limited to 3 segments
        assertEquals(3, segments.size)
        assertEquals("light on", segments[0])
        assertEquals("fan off", segments[1])
        assertEquals("sound badhao", segments[2])
    }

    @Test
    fun testCompoundCommandMinTokens() {
        val input = "light band karo aur music"
        val segments = assistant.splitCompoundCommand(input)
        
        // "music" is too short (1 token), so it should not be a separate segment
        assertEquals(1, segments.size)
        assertEquals("light band karo aur music", segments[0])
    }
}
