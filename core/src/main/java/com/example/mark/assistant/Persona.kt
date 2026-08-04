package com.example.mark.assistant

import android.util.Log

/**
 * Mark's voice.
 *
 * Every line exists twice — English and Hinglish — and the reply follows the
 * language the user spoke in. Answering "Samajh nahi aaya sir" to someone who
 * said "what's open on the laptop" feels like Mark wasn't listening, which is
 * the exact opposite of the impression an assistant should leave.
 *
 * Language is decided by [Lang], remembered for the session, and every line
 * here just picks from the right bank.
 */
object Persona {

    enum class Lang { EN, HI }

    /**
     * Sticky for the session. A half-heard command ("laptop") carries no
     * language signal of its own; without memory the reply language would
     * flip around mid-conversation, which reads as a bug.
     */
    @Volatile
    private var current: Lang = Lang.EN

    fun lang(): Lang = current

    fun setLang(l: Lang) {
        // Force EN for now
        current = Lang.EN
    }

    fun resetLang() {
        current = Lang.EN
    }

    // Hinglish function words. Latin-script Hindi has no character-set tell, so
    // detection is a vocabulary vote — these words basically never appear in an
    // English sentence.
    private val hinglishMarkers = setOf(
        "kar", "karo", "kardo", "kar", "de", "do", "dena", "diya", "kiya",
        "hai", "hain", "tha", "thi", "ho", "hoga", "hua", "raha", "rahi",
        "kya", "kyu", "kyun", "kaise", "kaisa", "kaisi", "kitna", "kitni", "kaun", "kahan",
        "mera", "meri", "mujhe", "tera", "teri", "aap", "tum", "tu", "apna",
        "khol", "kholo", "chala", "chalao", "band", "bandh", "batao", "bata",
        "dikha", "dikhao", "laga", "lagao", "bhej", "sun", "suno", "yaar", "bhai",
        "abhi", "phir", "wapas", "thoda", "zara", "aur", "bhi", "nahi", "nhi",
        "haan", "acha", "achha", "theek", "sahi", "jaldi", "dheere", "kam", "zyada",
        "pe", "par", "ka", "ki", "ko", "se", "me", "mein", "wala", "wali",
        "namaste", "haal"
    )

    // Words that only really show up in English phrasing.
    private val englishMarkers = setOf(
        "the", "is", "are", "was", "my", "your", "please", "can", "could",
        "what", "when", "where", "who", "how", "why", "which",
        "turn", "open", "close", "start", "stop", "play", "pause", "set",
        "show", "tell", "take", "make", "run", "send", "call", "read",
        "on", "off", "up", "down", "to", "for", "of", "and", "with",
        "increase", "decrease", "brightness", "volume", "battery", "laptop",
        "hello", "hi", "hey", "yo", "good", "morning", "evening", "afternoon", "night", "thanks", "thank"
    )

    /**
     * Decides the language of an utterance and remembers it.
     *
     * Hinglish wins ties: this is a Hinglish-first assistant, and a command like
     * "laptop lock kar" is mostly English nouns with one Hindi verb — the verb
     * is the signal that matters.
     */
    fun detectFrom(rawInput: String): Lang {
        val tokens = rawInput.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return current

        var hi = 0
        var en = 0
        for (t in tokens) {
            if (t in hinglishMarkers) hi++
            else if (t in englishMarkers) en++
        }

        val decided = when {
            hi > 0 -> Lang.HI          // any Hindi verb at all -> Hinglish
            en > 0 -> Lang.EN
            else -> current            // no signal: keep whatever we were on
        }
        setLang(decided)
        return decided
    }

    private fun bank(en: List<String>, hi: List<String>): List<String> =
        if (current == Lang.EN) en else hi

    // ---------------------------------------------------------------- banks

    private val ackDoneEn = listOf(
        "Done, sir.", "Done.", "Handled, sir.", "That's done.", "All set, sir.",
        "Consider it done.", "Taken care of.", "Right away, sir.", "Sorted."
    )
    private val ackDoneHi = listOf(
        "Ho gaya sir.", "Kar diya sir.", "Done sir.", "Ho gaya.", "Kar diya.",
        "Bilkul sir.", "Ho gaya, aur kuch?", "Kar diya sir, aur?"
    )

    private val ackFailEn = listOf(
        "That didn't work, sir.", "Couldn't do that.", "No luck there, sir.",
        "That one failed.", "Didn't go through, sir."
    )
    private val ackFailHi = listOf(
        "Nahi ho paya sir.", "Ye nahi hua sir.", "Fail ho gaya.",
        "Sorry sir, nahi chala.", "Ismein dikkat aa gayi sir."
    )

    private val notUnderstoodEn = listOf(
        "Didn't catch that, sir.", "Say that again?", "I missed that one.",
        "Once more, sir?", "That went past me, sir."
    )
    private val notUnderstoodHi = listOf(
        "Samajh nahi aaya sir.", "Ek baar phir boliye.", "Woh miss kar gaya sir.",
        "Dobara boliye sir.", "Ye samajh nahi aaya."
    )

    private val noPermissionEn = listOf(
        "I don't have permission for that, sir.",
        "That one needs permission, sir.",
        "Not allowed to do that yet, sir."
    )
    private val noPermissionHi = listOf(
        "Sir, permission nahi hai iske liye.",
        "Iske liye permission chahiye sir.",
        "Permission nahi mili sir."
    )

    private val thinkingEn = listOf("One moment, sir.", "Just a second.", "Working on it, sir.")
    private val thinkingHi = listOf("Ek second sir.", "Ruko sir.", "Dekh raha hun sir.")

    private val greetingEn = listOf(
        "Yes, sir?", "Here, sir.", "What do you need?",
        "At your service.", "Go ahead, sir.", "Listening, sir."
    )
    private val greetingHi = listOf(
        "Haan sir?", "Boliye sir.", "Kya chahiye sir?",
        "Hazir hun sir.", "Yes sir, boliye.", "Sun raha hun sir."
    )

    private val endSessionEn = listOf(
        "As you wish, sir.", "Signing off, sir.", "I'll be here.", "Alright, sir."
    )
    private val endSessionHi = listOf(
        "Theek hai sir.", "Chalo sir.", "Yahin hun sir.", "Thik hai, bula lena."
    )

    private val thanksEn = listOf(
        "Anytime, sir.", "Of course, sir.", "That's what I'm for.", "Pleasure, sir."
    )
    private val thanksHi = listOf(
        "Koi baat nahi sir.", "Iske liye hi hun sir.", "Anytime sir.", "Bas boliye sir."
    )

    // ---------------------------------------------------------------- api

    fun ackDone(): String = pickInternal("ACK_DONE", bank(ackDoneEn, ackDoneHi))
    fun ackFail(): String = pickInternal("ACK_FAIL", bank(ackFailEn, ackFailHi))
    fun notUnderstood(): String = pickInternal("NOT_UNDERSTOOD", bank(notUnderstoodEn, notUnderstoodHi))
    fun noPermission(): String = pickInternal("NO_PERMISSION", bank(noPermissionEn, noPermissionHi))
    fun thinking(): String = pickInternal("THINKING", bank(thinkingEn, thinkingHi))
    fun greeting(): String = pickInternal("GREETING", bank(greetingEn, greetingHi))
    fun endSession(): String = pickInternal("END_SESSION", bank(endSessionEn, endSessionHi))
    fun thanks(): String = pickInternal("THANKS", bank(thanksEn, thanksHi))

    /** Ad-hoc pair: pass both languages, the current one is used. */
    fun say(category: String, en: List<String>, hi: List<String>): String =
        pickInternal(category, bank(en, hi))

    fun say(category: String, en: String, hi: String): String =
        pickInternal(category, bank(listOf(en), listOf(hi)))

    /** Legacy call site support — options are used as-is, no language switch. */
    fun pick(category: String, vararg options: String): String =
        pickInternal(category, options.toList())

    private fun pickInternal(category: String, options: List<String>): String {
        if (options.isEmpty()) return ""
        val index = options.indices.random()
        Log.d("MarkPersona", "category=$category lang=$current index=$index")
        return options[index]
    }
}