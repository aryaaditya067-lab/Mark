package com.example.mark.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexIntentResolverGoldenTest {

    private val resolver = RegexIntentResolver()

    data class GoldenCase(
        val input: String,
        val expectedIntent: IntentType?,
        val expectedParams: Map<String, String> = emptyMap(),
        val description: String = ""
    )

    @Test
    fun runGoldenSuite() {
        val cases = listOf(
            // FLASHLIGHT
            GoldenCase("turn on flashlight", IntentType.TOGGLE_FLASHLIGHT, mapOf("state" to "on")),
            GoldenCase("flashlight jalao", IntentType.TOGGLE_FLASHLIGHT, mapOf("state" to "on")),
            GoldenCase("torch band kar", IntentType.TOGGLE_FLASHLIGHT, mapOf("state" to "off")),

            // BRIGHTNESS
            GoldenCase("set brightness to 80", IntentType.SET_BRIGHTNESS, mapOf("level" to "80")),
            GoldenCase("brightness badhao", IntentType.SET_BRIGHTNESS, mapOf("direction" to "up")),
            GoldenCase("chamak kam karo", IntentType.SET_BRIGHTNESS, mapOf("direction" to "down")),

            // VOLUME
            GoldenCase("increase volume", IntentType.SET_VOLUME, mapOf("direction" to "up")),
            GoldenCase("awaaz kam kar", IntentType.SET_VOLUME, mapOf("direction" to "down")),
            GoldenCase("volume 50 percent", IntentType.SET_VOLUME, mapOf("level" to "50")),

            // SILENT / DND
            GoldenCase("put phone on silent", IntentType.SET_SILENT, mapOf("state" to "on")),
            GoldenCase("mute kar do", IntentType.SET_SILENT, mapOf("state" to "on")),
            GoldenCase("turn on dnd", IntentType.SET_DND, mapOf("state" to "on")),
            GoldenCase("do not disturb band kar", IntentType.SET_DND, mapOf("state" to "off")),

            // ROTATE  (lock_state off == rotation enabled)
            GoldenCase("enable auto rotate", IntentType.SET_ROTATE, mapOf("state" to "off")),
            GoldenCase("rotation lock kar", IntentType.SET_ROTATE, mapOf("state" to "on")),

            // MEDIA CONTROL
            GoldenCase("play music", IntentType.MEDIA_CONTROL, mapOf("action" to "play")),
            GoldenCase("gaana rok do", IntentType.MEDIA_CONTROL, mapOf("action" to "pause")),
            GoldenCase("next song", IntentType.MEDIA_CONTROL, mapOf("action" to "next")),
            GoldenCase("pichla track chalao", IntentType.MEDIA_CONTROL, mapOf("action" to "previous")),

            // TIME MANAGER
            GoldenCase("what time is it", IntentType.TIME_MANAGER, mapOf("action" to "time")),
            GoldenCase("set a timer for 5 minutes", IntentType.TIME_MANAGER, mapOf("action" to "timer", "seconds" to "300")),
            GoldenCase("timer laga 10 minute ka", IntentType.TIME_MANAGER, mapOf("action" to "timer", "seconds" to "600")),
            GoldenCase("start stopwatch", IntentType.TIME_MANAGER, mapOf("action" to "stopwatch")),
            GoldenCase("stop the timer", IntentType.TIME_MANAGER, mapOf("action" to "stop")),

            // TIMER QUERY
            GoldenCase("how much time left on timer", IntentType.TIMER_QUERY),
            GoldenCase("timer kitna bacha hai", IntentType.TIMER_QUERY),

            // ALARM
            GoldenCase("set alarm for 7 am", IntentType.SET_ALARM, mapOf("time" to "07:00")),
            GoldenCase("subah 8 baje ka alarm laga", IntentType.SET_ALARM, mapOf("time" to "08:00")),

            // CALL / SMS
            GoldenCase("call mom", IntentType.CALL_CONTACT, mapOf("contact" to "mom")),
            GoldenCase("papa ko phone milao", IntentType.CALL_CONTACT, mapOf("contact" to "papa")),
            GoldenCase("message rahul hello how are you", IntentType.SEND_SMS, mapOf("payload" to "rahul hello how are you")),

            // APPS / HOME / SCREENSHOT
            GoldenCase("open whatsapp", IntentType.OPEN_APP, mapOf("app_name" to "whatsapp")),
            GoldenCase("calculator kholo", IntentType.OPEN_APP, mapOf("app_name" to "calculator")),
            GoldenCase("go home", IntentType.GO_HOME),
            GoldenCase("take a screenshot", IntentType.TAKE_SCREENSHOT),

            // BATTERY / WEATHER
            GoldenCase("check battery percentage", IntentType.GET_BATTERY),
            GoldenCase("phone ki battery kitni hai", IntentType.GET_BATTERY),
            GoldenCase("how is the weather", IntentType.GET_WEATHER),
            GoldenCase("mausam kaisa hai", IntentType.GET_WEATHER),

            // HEALTH
            GoldenCase("how many steps today", IntentType.GET_STEPS),
            GoldenCase("aaj kitna chala", IntentType.GET_STEPS),
            GoldenCase("check heart rate", IntentType.GET_HEART_RATE),
            GoldenCase("dhadkan check kar", IntentType.GET_HEART_RATE),
            GoldenCase("how did i sleep", IntentType.GET_SLEEP),

            // CALENDAR / TASKS
            GoldenCase("show my calendar", IntentType.GET_CALENDAR),
            GoldenCase("add task buy milk", IntentType.ADD_TASK, mapOf("content" to "buy milk")),
            GoldenCase("mere tasks dikhao", IntentType.GET_TASKS),
            GoldenCase("mark task 2 as done", IntentType.COMPLETE_TASK, mapOf("index" to "2")),

            // RING PHONE (both word orders)
            GoldenCase("ring my phone", IntentType.RING_PHONE),
            GoldenCase("mera phone kahan hai", IntentType.RING_PHONE),

            // NOTIFICATIONS (both word orders)
            GoldenCase("read my notifications", IntentType.READ_NOTIFICATIONS),
            GoldenCase("notifs batao", IntentType.READ_NOTIFICATIONS),

            // NAVIGATION
            GoldenCase("navigate to delhi", IntentType.NAVIGATE_TO, mapOf("destination" to "delhi")),
            GoldenCase("delhi ka rasta dikhao", IntentType.NAVIGATE_TO, mapOf("destination" to "delhi")),
            GoldenCase("distance to mumbai", IntentType.GET_DISTANCE, mapOf("destination" to "mumbai")),
            GoldenCase("nearby hospitals", IntentType.FIND_NEARBY, mapOf("placeType" to "hospitals")),

            // SYSTEM / HELP / CONVERSATIONAL
            GoldenCase("watch status", IntentType.WATCH_STATUS),
            GoldenCase("help me", IntentType.HELP),
            GoldenCase("repeat that", IntentType.REPEAT),
            GoldenCase("undo last action", IntentType.UNDO),
            GoldenCase("yes do it", IntentType.CONFIRMATION, mapOf("decision" to "yes")),
            GoldenCase("nahi mat kar", IntentType.CONFIRMATION, mapOf("decision" to "no")),

            // EASTER EGGS / GREETING / SESSION
            GoldenCase("who are you", IntentType.EASTER_EGG, mapOf("type" to "who")),
            GoldenCase("are you jarvis", IntentType.EASTER_EGG, mapOf("type" to "jarvis")),
            GoldenCase("hello mark", IntentType.GREETING),
            GoldenCase("namaste", IntentType.GREETING),
            GoldenCase("bye mark", IntentType.END_SESSION),

            // ===== PHASE 3B =====
            // NEGATION — must tag negated=true so the controller suppresses execution
            GoldenCase("torch mat chalana", IntentType.TOGGLE_FLASHLIGHT, mapOf("negated" to "true")),
            GoldenCase("brightness mat badhana", IntentType.SET_BRIGHTNESS, mapOf("negated" to "true")),
            GoldenCase("flashlight nahi chahiye", IntentType.TOGGLE_FLASHLIGHT, mapOf("negated" to "true")),
            GoldenCase("volume mat kam karna", IntentType.SET_VOLUME, mapOf("negated" to "true")),
            // negation words in a DESCRIPTIVE command must NOT suppress
            GoldenCase("kuch dikh nahi raha light badha", IntentType.SET_BRIGHTNESS, mapOf("direction" to "up")),

            // QUESTION-WRAPPER STRIPPING
            GoldenCase("kya tu torch chala sakta hai", IntentType.TOGGLE_FLASHLIGHT),
            GoldenCase("can you turn on the flashlight", IntentType.TOGGLE_FLASHLIGHT, mapOf("state" to "on")),
            GoldenCase("zara volume kam kar do", IntentType.SET_VOLUME, mapOf("direction" to "down")),
            // HELP is itself a question — the wrapper must survive
            GoldenCase("kya kya kar sakta hai", IntentType.HELP),

            // RELATIVE / DELTA
            GoldenCase("thoda brightness badha", IntentType.SET_BRIGHTNESS, mapOf("direction" to "up", "amount" to "10")),
            GoldenCase("volume bahut kam kar", IntentType.SET_VOLUME, mapOf("direction" to "down", "amount" to "25")),

            // CONTEXT FOLLOW-UP — bare phrases with no object of their own.
            // These must NOT be stolen by MEDIA_CONTROL's generic "band"/"stop".
            GoldenCase("ab band kar", IntentType.CONTEXT_FOLLOW_UP, mapOf("ctx" to "off")),
            GoldenCase("band kar de", IntentType.CONTEXT_FOLLOW_UP, mapOf("ctx" to "off")),
            GoldenCase("aur badha", IntentType.CONTEXT_FOLLOW_UP, mapOf("ctx" to "up")),
            GoldenCase("aur kam", IntentType.CONTEXT_FOLLOW_UP, mapOf("ctx" to "down")),
            // ...but a phrase that NAMES its target must go to the normal rule
            GoldenCase("gaana band kar", IntentType.MEDIA_CONTROL, mapOf("action" to "pause")),
            GoldenCase("torch band kar", IntentType.TOGGLE_FLASHLIGHT, mapOf("state" to "off")),

            // TIME EXPRESSIONS (verified by simulation)
            GoldenCase("paune 8 ka alarm laga", IntentType.SET_ALARM, mapOf("time" to "07:45")),
            GoldenCase("sawa 9 baje ka alarm laga", IntentType.SET_ALARM, mapOf("time" to "09:15")),
            GoldenCase("7 baj kar 20 ka alarm laga", IntentType.SET_ALARM, mapOf("time" to "07:20")),
            GoldenCase("10 minute baad alarm laga", IntentType.SET_ALARM, mapOf("relative_minutes" to "10")),

            // ===== LAPTOP (third device) — 85 phrasings, English + Hinglish =====
            // Every rule requires the laptop anchor, so the non-laptop cases
            // further down must keep resolving exactly as they did before.
            GoldenCase("laptop lock kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "lock")),
            GoldenCase("lock the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "lock")),
            GoldenCase("lock kar laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "lock")),
            GoldenCase("laptop band kar de", IntentType.LAPTOP_CONTROL, mapOf("action" to "lock")),
            GoldenCase("computer lock kar do", IntentType.LAPTOP_CONTROL, mapOf("action" to "lock")),
            GoldenCase("laptop sula de", IntentType.LAPTOP_CONTROL, mapOf("action" to "sleep")),
            GoldenCase("put the laptop to sleep", IntentType.LAPTOP_CONTROL, mapOf("action" to "sleep")),
            GoldenCase("pc sleep kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "sleep")),
            GoldenCase("laptop hibernate kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "sleep")),
            GoldenCase("laptop ki screen band kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "screen_off")),
            GoldenCase("screen off on laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "screen_off")),
            GoldenCase("laptop display band kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "screen_off")),
            GoldenCase("laptop shutdown kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "shutdown")),
            GoldenCase("shut down the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "shutdown")),
            GoldenCase("laptop power off kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "shutdown")),
            GoldenCase("turn off the pc", IntentType.LAPTOP_CONTROL, mapOf("action" to "shutdown")),
            GoldenCase("switch off the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "shutdown")),
            GoldenCase("laptop restart kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "restart")),
            GoldenCase("restart the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "restart")),
            GoldenCase("pc reboot kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "restart")),
            GoldenCase("laptop dobara chalu kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "restart")),
            GoldenCase("laptop log off kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "logoff")),
            GoldenCase("log out of the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "logoff")),
            GoldenCase("laptop cancel kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "cancel_shutdown")),
            GoldenCase("cancel shutdown on laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "cancel_shutdown")),
            GoldenCase("laptop rehne de", IntentType.LAPTOP_CONTROL, mapOf("action" to "cancel_shutdown")),
            GoldenCase("abort the laptop shutdown", IntentType.LAPTOP_CONTROL, mapOf("action" to "cancel_shutdown")),
            GoldenCase("laptop pe chrome khol", IntentType.LAPTOP_CONTROL, mapOf("action" to "open")),
            GoldenCase("open chrome on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "open")),
            GoldenCase("laptop pe vs code khol", IntentType.LAPTOP_CONTROL, mapOf("action" to "open")),
            GoldenCase("open vscode on laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "open")),
            GoldenCase("laptop pe spotify khol", IntentType.LAPTOP_CONTROL, mapOf("action" to "open")),
            GoldenCase("open notepad on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "open")),
            GoldenCase("laptop pe terminal khol", IntentType.LAPTOP_CONTROL, mapOf("action" to "open")),
            GoldenCase("open steam on pc", IntentType.LAPTOP_CONTROL, mapOf("action" to "open")),
            GoldenCase("laptop pe chrome band kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "close")),
            GoldenCase("close chrome on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "close")),
            GoldenCase("laptop pe spotify quit kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "close")),
            GoldenCase("kill discord on laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "close")),
            GoldenCase("laptop pe notepad close kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "close")),
            GoldenCase("laptop pe window switch kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "switch")),
            GoldenCase("alt tab on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "switch")),
            GoldenCase("switch apps on laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "switch")),
            GoldenCase("laptop pe downloads khol", IntentType.LAPTOP_CONTROL, mapOf("action" to "folder")),
            GoldenCase("open downloads on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "folder")),
            GoldenCase("laptop pe documents khol", IntentType.LAPTOP_CONTROL, mapOf("action" to "folder")),
            GoldenCase("laptop pe projects khol", IntentType.LAPTOP_CONTROL, mapOf("action" to "folder")),
            GoldenCase("laptop pe gaana pause kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "media")),
            GoldenCase("pause the music on laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "media")),
            GoldenCase("play music on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "media")),
            GoldenCase("laptop pe next track", IntentType.LAPTOP_CONTROL, mapOf("action" to "media")),
            GoldenCase("laptop pe volume kam kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "media")),
            GoldenCase("turn the laptop volume up", IntentType.LAPTOP_CONTROL, mapOf("action" to "media")),
            GoldenCase("laptop ka screenshot le", IntentType.LAPTOP_CONTROL, mapOf("action" to "screenshot")),
            GoldenCase("take a screenshot on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "screenshot")),
            GoldenCase("laptop pe screen capture kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "screenshot")),
            GoldenCase("laptop pe recording shuru kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "screen_record")),
            GoldenCase("record the laptop screen", IntentType.LAPTOP_CONTROL, mapOf("action" to "screen_record")),
            GoldenCase("laptop ki brightness kam kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "brightness")),
            GoldenCase("increase brightness on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "brightness")),
            GoldenCase("laptop brightness badha", IntentType.LAPTOP_CONTROL, mapOf("action" to "brightness")),
            GoldenCase("laptop ki chamak kam kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "brightness")),
            GoldenCase("laptop dark mode on kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "dark_mode")),
            GoldenCase("turn on dark mode on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "dark_mode")),
            GoldenCase("enable night mode on laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "dark_mode")),
            GoldenCase("laptop ka wifi band kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "wifi")),
            GoldenCase("turn off wifi on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "wifi")),
            GoldenCase("laptop ka wifi on kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "wifi")),
            GoldenCase("laptop pe youtube khol", IntentType.LAPTOP_CONTROL, mapOf("action" to "browser")),
            GoldenCase("open youtube on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "browser")),
            GoldenCase("laptop pe python tutorial search kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "browser")),
            GoldenCase("laptop pe yt khol", IntentType.LAPTOP_CONTROL, mapOf("action" to "browser")),
            GoldenCase("laptop pe build chala", IntentType.LAPTOP_CONTROL, mapOf("action" to "gradle")),
            GoldenCase("run the build on laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "gradle")),
            GoldenCase("laptop pe clean kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "gradle")),
            GoldenCase("compile the project on laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "gradle")),
            GoldenCase("laptop ka clipboard padh", IntentType.LAPTOP_CONTROL, mapOf("action" to "clipboard_get")),
            GoldenCase("read the clipboard on laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "clipboard_get")),
            GoldenCase("laptop pe ye type kar", IntentType.LAPTOP_CONTROL, mapOf("action" to "type")),
            GoldenCase("type this on the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "type")),
            GoldenCase("laptop pe kya khula hai", IntentType.LAPTOP_CONTROL, mapOf("action" to "foreground")),
            GoldenCase("kaunsa app khula hai laptop pe", IntentType.LAPTOP_CONTROL, mapOf("action" to "foreground")),
            GoldenCase("laptop kaisa hai", IntentType.LAPTOP_CONTROL, mapOf("action" to "status")),
            GoldenCase("how is the laptop", IntentType.LAPTOP_CONTROL, mapOf("action" to "status")),
            GoldenCase("laptop ka status batao", IntentType.LAPTOP_CONTROL, mapOf("action" to "status")),
            // ...and without the laptop word, nothing changes
            GoldenCase("screenshot le", IntentType.TAKE_SCREENSHOT),
            GoldenCase("chrome khol", IntentType.OPEN_APP),
            GoldenCase("gaana pause kar", IntentType.MEDIA_CONTROL, mapOf("action" to "pause")),
            GoldenCase("volume kam kar", IntentType.SET_VOLUME, mapOf("direction" to "down")),

            // "do" — Hindi 2 vs imperative "kar do"
            GoldenCase("do minute ka timer laga", IntentType.TIME_MANAGER, mapOf("action" to "timer", "seconds" to "120")),
            GoldenCase("volume kam kar do", IntentType.SET_VOLUME, mapOf("direction" to "down"))
        )

        var passed = 0
        var total = 0
        val failures = mutableListOf<String>()

        cases.forEach { case ->
            total++
            val result = resolver.resolve(case.input)

            try {
                if (case.expectedIntent == null) {
                    assertNull("Expected null intent for '${case.input}'", result.intent)
                } else {
                    assertNotNull("Expected intent ${case.expectedIntent} for '${case.input}' but got null", result.intent)
                    assertEquals("Intent mismatch for '${case.input}'", case.expectedIntent, result.intent?.type)

                    case.expectedParams.forEach { (key, value) ->
                        assertTrue("Param '$key' missing for '${case.input}'", result.intent?.params?.containsKey(key) == true)
                        assertEquals("Param value mismatch for '$key' in '${case.input}'", value, result.intent?.params?.get(key))
                    }
                }
                passed++
            } catch (e: Throwable) {
                failures.add(
                    "Phrase: \"${case.input}\"\n" +
                            "  Expected: ${case.expectedIntent} ${case.expectedParams}\n" +
                            "  Actual:   ${result.intent?.type} ${result.intent?.params}\n" +
                            "  Error: ${e.message}"
                )
            }
        }

        println("Summary: $passed / $total passed")
        failures.forEach { println("\n$it") }

        if (failures.isNotEmpty()) {
            throw AssertionError("Golden suite failed with ${failures.size} errors")
        }
    }

    @Test
    fun testNormalization() {
        // Expectations are the ACTUAL normalized output, not aspirational strings.
        val cases = listOf(
            "please turn on the flashlight" to "flashlight",   // filler stripping keeps the anchor
            "flash light" to "flashlight",                      // compound joins
            "blue tooth" to "bluetooth",
            "do not disturb" to "dnd",
            "heart rate" to "heartrate",
            "paanch minute ka timer" to "5 minute",             // word numbers ("ka" is a filler)
            "set brightness to full" to "100"                   // level words near an anchor
        )

        cases.forEach { (input, expectedSub) ->
            val result = resolver.resolve(input)
            assertTrue(
                "Normalization failed for '$input'. Expected to contain '$expectedSub', got '${result.normalizedText}'",
                result.normalizedText.contains(expectedSub)
            )
        }

        // "hey mark" alone is only the wake prefix — it must still resolve, not crash or
        // fall through to UNKNOWN.
        val bare = resolver.resolve("hey mark")
        assertEquals(IntentType.GREETING, bare.intent?.type)
    }

    @Test
    fun testNegativeCases() {
        val negatives = listOf(
            "zindagi ka matlab kya hai",
            "explain quantum physics",
            "mujhe kal ke baare mein kuch batao",
            "who won the match yesterday"
        )

        negatives.forEach { input ->
            val result = resolver.resolve(input)
            assertTrue(
                "Negative case '$input' should not resolve with high confidence (got ${result.intent?.type} @ ${result.confidence})",
                result.intent == null || result.confidence < 0.20f
            )
        }
    }

    @Test
    fun testCollisions() {
        assertEquals(IntentType.TIME_MANAGER, resolver.resolve("stop the timer").intent?.type)
        assertEquals(IntentType.GO_HOME, resolver.resolve("sab band kar").intent?.type)
        assertEquals(IntentType.SET_SILENT, resolver.resolve("aawaz band kar").intent?.type)
        assertEquals(IntentType.GET_STEPS, resolver.resolve("aaj kitna chala").intent?.type)
        assertEquals(IntentType.TOGGLE_FLASHLIGHT, resolver.resolve("light chala").intent?.type)
        assertEquals(IntentType.SET_BRIGHTNESS, resolver.resolve("light badha").intent?.type)
    }
}