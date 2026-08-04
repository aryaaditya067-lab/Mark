package com.example.mark.assistant

import com.example.mark.router.IntentType

/**
 * Phrases tool output as something Mark would say, without an LLM round trip.
 *
 * Every user-facing string here comes in both languages and goes through
 * [Persona], which follows whichever language the user spoke in.
 *
 * Templates only exist for intents the router can resolve offline. Anything
 * else went through Groq and already has natural language.
 */
object ResponseTemplates {

    private val en get() = Persona.lang() == Persona.Lang.EN

    /**
     * Null when there is no template for this result — the caller should fall
     * back to [ToolResult.text], which is always readable if plain.
     */
    fun render(intent: IntentType, result: ToolResult): String? {
        if (result !is ToolResult.Success) {
            return when (result) {
                is ToolResult.Failure -> {
                    if (result.reason == "no_permission") Persona.noPermission()
                    else Persona.ackFail()
                }
                else -> null
            }
        }

        return when (intent) {
            IntentType.GREETING -> Persona.greeting()

            IntentType.GET_STEPS -> steps(result)
            IntentType.GET_HEART_RATE -> heartRate(result)
            IntentType.GET_WEATHER -> result.text
            IntentType.ADD_TASK -> Persona.say(
                "ADD_TASK",
                listOf("Added to your list, sir.", "Noted, sir.", "On the list."),
                listOf("List mein daal diya sir.", "Note kar liya sir.", "Add kar diya.")
            )
            IntentType.GET_TASKS -> tasks(result)
            IntentType.COMPLETE_TASK -> Persona.say(
                "COMPLETE_TASK",
                listOf("Marked done, sir.", "Ticked off.", "That's off the list."),
                listOf("Done mark kar diya sir.", "List se hata diya.", "Ho gaya sir.")
            )
            IntentType.GET_CALENDAR -> result.text
            IntentType.GET_SLEEP -> result.text

            // Silent-confirm intents. These normally reply with a haptic only;
            // the text still matters for the phone's chat bubble.
            IntentType.SET_BRIGHTNESS,
            IntentType.SET_SILENT,
            IntentType.SET_ROTATE,
            IntentType.MEDIA_CONTROL,
            IntentType.TIME_MANAGER,
            IntentType.OPEN_APP,
            IntentType.TOGGLE_FLASHLIGHT,
            IntentType.SET_VOLUME,
            IntentType.SET_DND,
            IntentType.SET_ALARM,
            IntentType.GO_HOME,
            IntentType.TAKE_SCREENSHOT,
            IntentType.RING_PHONE -> Persona.ackDone()

            IntentType.GET_BATTERY -> result.text
            IntentType.LAPTOP_CONTROL -> laptop(result)

            IntentType.END_SESSION -> Persona.endSession()
            IntentType.EASTER_EGG -> easterEgg(result)
            IntentType.REPEAT -> result.text
            IntentType.UNDO -> Persona.say(
                "UNDO",
                listOf("Put it back, sir.", "Reverted.", "Back to how it was."),
                listOf("Wapas kar diya sir.", "Pehle jaisa kar diya.", "Undo ho gaya sir.")
            )
            IntentType.TIMER_QUERY -> result.text
            IntentType.WATCH_STATUS -> result.text
            IntentType.CONTEXT_FOLLOW_UP -> Persona.ackDone()

            IntentType.HELP -> if (en) {
                "Torch, brightness, volume, calls, messages, alarms, timers, music, " +
                        "weather, steps — and your laptop. Just say the word, sir."
            } else {
                "Torch, brightness, volume, call, message, alarm, timer, music, weather, " +
                        "steps — aur aapka laptop bhi. Bas bol dijiye sir."
            }

            else -> null
        }
    }

    /**
     * The laptop agent replies in English. Left as-is when Mark is speaking
     * English; wrapped in a short Hinglish acknowledgement otherwise, so the
     * reply doesn't switch language mid-sentence.
     */
    private fun laptop(result: ToolResult): String {
        val raw = result.text
        if (en || raw.isBlank()) return raw
        return when (result.data["action"]) {
            "lock" -> "Laptop lock kar diya sir."
            "sleep" -> "Laptop so gaya sir."
            "shutdown" -> "Laptop tees second mein band ho jayega sir. Cancel bol dijiye rokna ho toh."
            "restart" -> "Laptop restart ho raha hai sir."
            "cancel_shutdown" -> "Rok diya sir."
            "open" -> "Khol diya sir."
            "close" -> "Band kar diya sir."
            "folder" -> "Folder khol diya sir."
            "media" -> "Ho gaya sir."
            "screenshot" -> "Screenshot le liya sir."
            "brightness" -> "Brightness set kar di sir."
            "dark_mode" -> "Theme badal di sir."
            "gradle" -> "Build chala diya sir."
            else -> raw
        }
    }

    private fun easterEgg(result: ToolResult): String {
        return when (result.data["type"] ?: "") {
            "jarvis" -> Persona.say(
                "EGG_JARVIS",
                listOf(
                    "Mark, sir. Jarvis is a cousin of mine.",
                    "Not quite, sir. Jarvis had a bigger budget.",
                    "Mark, sir. Jarvis works for someone else."
                ),
                listOf(
                    "Mark, sir. Jarvis mera cousin hai.",
                    "Nahi sir, Mark. Jarvis ka budget bada tha.",
                    "Mark hun sir. Jarvis kisi aur ke paas kaam karta hai."
                )
            )
            "who" -> Persona.say(
                "EGG_WHO",
                listOf(
                    "I'm Mark, sir. Your assistant.",
                    "Mark, sir. I run your watch, your phone and your laptop.",
                    "Mark. Built by you, sir."
                ),
                listOf(
                    "Main Mark hoon sir, aapka assistant.",
                    "Mark sir. Watch, phone aur laptop — teeno main sambhalta hun.",
                    "Mark hun sir. Aapne hi banaya hai."
                )
            )
            "love" -> Persona.say(
                "EGG_LOVE",
                listOf("Noted, sir.", "Duly noted, sir.", "I'll add it to the log, sir."),
                listOf("Noted, sir.", "Note kar liya sir.", "Log mein daal diya sir.")
            )
            "thanks" -> Persona.thanks()
            "night" -> Persona.say(
                "EGG_NIGHT",
                listOf("Good night, sir.", "Good night. I'll be here.", "Rest well, sir."),
                listOf("Good night sir.", "Good night, yahin hun sir.", "So jaiye sir.")
            )
            "how" -> Persona.say(
                "EGG_HOW",
                listOf(
                    "All systems fine, sir. You?",
                    "Running well, sir. Go ahead.",
                    "Nothing broken so far, sir."
                ),
                listOf(
                    "Sab theek sir. Aap boliye.",
                    "Badhiya sir. Kya chahiye?",
                    "Sab chal raha hai sir."
                )
            )
            else -> result.text
        }
    }

    private fun steps(result: ToolResult): String? {
        val n = result.data["steps"] ?: return null
        return Persona.say(
            "STEPS",
            listOf("You've walked $n steps today.", "$n steps so far today.", "You're at $n steps, sir."),
            listOf("Aaj $n steps ho gaye sir.", "$n kadam chal chuke hain aaj.", "$n steps sir.")
        )
    }

    private fun heartRate(result: ToolResult): String? {
        val bpm = result.data["bpm"] ?: return null
        return Persona.say(
            "HEART_RATE",
            listOf("Your heart rate is $bpm bpm.", "$bpm bpm right now.", "Reading $bpm bpm, sir."),
            listOf("Dhadkan $bpm bpm hai sir.", "$bpm bpm chal raha hai abhi.", "$bpm bpm sir.")
        )
    }

    private fun tasks(result: ToolResult): String? {
        val count = result.data["count"]?.toIntOrNull() ?: return null
        if (count == 0) {
            return Persona.say(
                "TASKS_EMPTY",
                listOf("Nothing pending, sir.", "Your list is clear.", "No tasks right now."),
                listOf("Kuch pending nahi sir.", "List khaali hai sir.", "Koi kaam baaki nahi.")
            )
        }
        val lead = if (en) {
            if (count == 1) "One task pending:" else "$count tasks pending:"
        } else {
            if (count == 1) "Ek kaam baaki hai:" else "$count kaam baaki hain:"
        }
        return "$lead\n${result.text}"
    }
}