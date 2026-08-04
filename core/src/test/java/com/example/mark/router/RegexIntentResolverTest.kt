package com.example.mark.router

import org.junit.Assert.*
import org.junit.Test

class RegexIntentResolverTest {
    private val resolver = RegexIntentResolver()

    @Test
    fun testConfirmationIntents() {
        val yesMatch = resolver.resolve("ji haan")
        assertEquals(IntentType.CONFIRMATION, yesMatch.intent?.type)
        assertEquals("yes", yesMatch.intent?.params?.get("decision"))

        val noMatch = resolver.resolve("mat kar")
        assertEquals(IntentType.CONFIRMATION, noMatch.intent?.type)
        assertEquals("no", noMatch.intent?.params?.get("decision"))
    }

    @Test
    fun testUndoIntent() {
        val undoMatch = resolver.resolve("pehle jaisa kar")
        assertEquals(IntentType.UNDO, undoMatch.intent?.type)
    }

    @Test
    fun testRepeatIntent() {
        val repeatMatch = resolver.resolve("phir se bol")
        assertEquals(IntentType.REPEAT, repeatMatch.intent?.type)
    }

    @Test
    fun testEasterEggIntents() {
        val jarvisMatch = resolver.resolve("are you jarvis")
        assertEquals(IntentType.EASTER_EGG, jarvisMatch.intent?.type)
        assertEquals("jarvis", jarvisMatch.intent?.params?.get("type"))
    }

    @Test
    fun testTimerQueryIntent() {
        val timerMatch = resolver.resolve("kitna time bacha")
        assertEquals(IntentType.TIMER_QUERY, timerMatch.intent?.type)
    }

    @Test
    fun testWatchStatusIntent() {
        val statusMatch = resolver.resolve("sab theek hai")
        assertEquals(IntentType.WATCH_STATUS, statusMatch.intent?.type)
    }

    @Test
    fun testHelpIntent() {
        val helpMatch = resolver.resolve("kya kar sakta hai")
        assertEquals(IntentType.HELP, helpMatch.intent?.type)
    }

    @Test
    fun testStepsIntent() {
        val stepsMatch = resolver.resolve("kitna chala")
        assertEquals(IntentType.GET_STEPS, stepsMatch.intent?.type)
    }

    @Test
    fun testAddTaskIntent() {
        val match = resolver.resolve("task add kar buy milk and bread")
        assertEquals(IntentType.ADD_TASK, match.intent?.type)
        // Note: content extraction depends on rawInput which is not passed to resolve in the current test setup.
        // I should probably pass rawInput to resolve.
    }

    @Test
    fun testGetTasksIntent() {
        val match = resolver.resolve("task dikha")
        assertEquals(IntentType.GET_TASKS, match.intent?.type)
    }

    @Test
    fun testCompleteTaskIntent() {
        val match = resolver.resolve("task complete 2")
        assertEquals(IntentType.COMPLETE_TASK, match.intent?.type)
        assertEquals("2", match.intent?.params?.get("index"))
    }
}
