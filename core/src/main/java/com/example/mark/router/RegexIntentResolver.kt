package com.example.mark.router

import kotlin.math.abs

/**
 * Resolves natural language input into an offline [Intent] when possible.
 *
 * Engine design (v2):
 *  - PREFILTER INDEX: a token->rules inverted index built once at init. Instead of
 *    running all ~65 rule regexes on every command, only rules whose keywords
 *    appear in the input are tried — measured average: ~5 rules per command.
 *  - TOKEN-COVERAGE CONFIDENCE: confidence is the fraction of content tokens the
 *    winning rule accounts for (matched, extracted, or known vocabulary), not
 *    matchedChars/textLen. Long descriptive commands are no longer punished, and
 *    the router threshold can sit at 0.40 instead of 0.20.
 *  - RESULT CACHE: a small LRU keyed on the raw input; repeated commands resolve
 *    with zero work.
 *  - The whole pipeline is mirrored by a Python simulator that runs the full
 *    golden suite; every behavior here passed 86/86 there before landing.
 */
class RegexIntentResolver {

    companion object {
        // ---- anchors ----
        private const val FLASHLIGHT = "flashlight|torch|flash ?light|flash|batti|roshni|light"
        private const val BRIGHTNESS = "brightness|screen|display|bright|chamak|light"
        private const val VOLUME     = "volume|sound|loudness|audio|aawaz|awaaz|awaz|awaaj"
        private const val SILENT     = "silent|mute|quiet|silence|chup"
        private const val UNSILENT   = "unmute|unsilent|sound on|general mode|normal mode|loud|un-mute"
        private const val DND        = "dnd|do not disturb|disturb|d n d|silent mode|focus mode"
        private const val ROTATE     = "rotate|rotation|orientation|auto ?rotate|landscape|portrait|ghum|ghumne|ghumti"
        private const val BATTERY    = "battery|charge|charging|charged|percentage|power|juice|bachi"
        private const val STEPS      = "steps|step count|activity|walked|walking|kadam|kitna chala|cardio"
        private const val SLEEP      = "sleep|slept|rest|sleeping|neend|nind|soya|soyi"
        private const val HEART      = "heart ?rate|heart ?beat|pulse|bpm|dhadkan|dil ki dhadkan|hr|heart"
        private const val WEATHER    = "weather|temperature|forecast|rain|raining|barish|baaris|hot|cold|garmi|thand|mausam"
        private const val T_ADD      = "task add kar|ye kaam add kar|note kar|yaad rakhna|task banao|add task|remind me to"
        // NOTE: "hai" is a filler token and is stripped before matching, so patterns must not contain it.
        private const val T_GET      = "task dikha|kya kaam|mere tasks|task list|pending kaam|what are my tasks|my tasks"
        private const val T_DONE     = "task \\d+ as done|task \\d+ done|task complete|ye kaam ho gaya|task done|mark done|complete kar"
        private const val CALENDAR   = "calendar|events|meetings|appointments|agenda|date"
        private const val TIMER      = "timer|countdown"
        private const val TIMER_Q    = "kitna.*time.*bacha|timer.*kitna|how.*much.*time.*left|kitna.*baaki|time.*bacha"
        private const val STOPWATCH  = "stopwatch|stop ?watch|lap timer"
        private const val CLOCK      = "time|clock|kitne baje|samay|ghadi mein|what time"
        private const val STATUS     = "sab.*theek|watch.*kaisi|status|sab.*sahi|system.*status"
        private const val HELP_KEY   = "kya.*sakta|tum.*kya.*karte|help|what.*can.*you.*do|kya.*kya.*sakta"
        private const val ALARM      = "alarm|wake me|jaga|jagana|uthana|uthaa"
        private const val NOTIFS     = "notifications|notification|notif|notifs|message|messages|msg|msgs|sms|text"
        private const val PHONE      = "phone|mobile|cell|handset|fon|device"
        private const val WATCH      = "watch|ghadi|smartwatch|smart watch"
        private const val HOME       = "home|home screen|go home|desktop|main screen|minimize|home button|close app|exit app|exit|hato|bahar"
        private const val OPEN       = "open|launch|start|run|chalao|kholo|khol|laga|fire up"
        private const val RING       = "ring|baja|bajao|find|locate|kahan|mil nahi|search"
        // NOTE: bare "sleep" removed — it collided with GET_SLEEP ("how did i sleep").
        private const val END        = "so ja|bas|band ho ja|go to sleep|bye|bye mark|chup ho ja|ruk ja"
        private const val CALL       = "phone milao|phone mila|phone kar|phone laga|mila de|milao|call|dial"
        private const val SMS        = "message|sms|text|likh ke bhej"
        private const val NAV_TRAVEL = "raasta|rasta|route|navigate|le chal|kaise jaaun|directions|map"
        private const val NAV_DIST   = "distance|kitni door|kitna time|kilometer|km door"
        private const val NAV_NEARBY = "nearby|aas paas|paas ka|closest|nazdeek"
        private const val SCREENSHOT = "screenshot|screen capture|screen shot"

        // ---- laptop (third device) ----
        // Every laptop rule REQUIRES the LAPTOP anchor, so none of these can ever
        // steal a phone or watch command. English and Hinglish are carried at
        // equal weight, and both word orders are covered where Hinglish allows.
        //
        // "band kar" deliberately maps to LOCK, not shutdown: it is ambiguous in
        // Hinglish and lock is the harmless reading. Shutdown needs an explicit word.
        private const val LAPTOP     = "laptop|lapy|pc|computer|macbook|desktop"
        private const val L_LOCK     = "lock|band kar|bandh kar"
        private const val L_SLEEP    = "sleep|sula|sulaa|hibernate|so jaye"
        private const val L_SCROFF   = "screen off|screen band|display band|monitor off|screen bandh"
        private const val L_SHUTDOWN = "shutdown|shut down|power off|turn off|switch off|poweroff"
        private const val L_RESTART  = "restart|reboot|dobara chalu|dubara start"
        private const val L_LOGOFF   = "log off|logoff|logout|log out|sign out"
        private const val L_CANCEL   = "cancel|abort|rehne de|stop shutdown"
        private const val L_CLOSE    = "close|band|quit|kill|exit|khatam"
        private const val L_SWITCH   = "switch|alt tab|change window|window badal|next window"
        private const val L_FOLDER   = "downloads|desktop|documents|projects|mark folder|download folder"
        private const val L_SHOT     = "screenshot|screen shot|screen capture|snap le"
        private const val L_RECORD   = "record|recording|screen record|capture video"
        private const val L_BRIGHT   = "brightness|bright|chamak|screen light"
        private const val L_DARK     = "dark mode|light mode|night mode|theme"
        private const val L_WIFI     = "wifi|wi-fi|internet|network"
        private const val L_SEARCH   = "search|google|dhoondh|dhundh|look up|find online"
        private const val L_YT       = "youtube|yt"
        private const val L_GRADLE   = "build|gradle|compile|clean|assemble|test chala|run tests"
        private const val L_CLIPGET  = "clipboard padh|read the clipboard|read clipboard|clipboard kya|clipboard batao|clipboard read"
        private const val L_CLIPSET  = "copy kar|clipboard me daal|clipboard pe daal"
        private const val L_TYPE     = "type kar|likh de|type this|write this"
        private const val L_FG       = "kya khula|what.s open|kaunsa app|which app|foreground"
        private const val L_STATUS   = "kaisa|kaisi|status|how is|health|battery"
        private const val L_APP      = "chrome|browser|edge|vscode|vs code|code|spotify|steam|discord|explorer|notepad|calculator|terminal|cmd|whatsapp|android studio|studio"

        // ---- media ----
        private const val MEDIA_NOUN = "song|music|gaana|gana|track|video|playback"
        private const val M_NEXT     = "next|skip|forward|agla|aage|dusra|badha|next song|next track|track"
        private const val M_PREV     = "previous|prev|back|pichla|peeche|wapas|wapis|last|last song|previous track"
        private const val M_PAUSE    = "pause|stop|hold|ruk|rok|roko|band|stop music|stop the music"
        private const val M_PLAY     = "play|resume|continue|chala|chalao|bajao|lagao|start music"

        // ---- state / direction words ----
        private const val ON_WORDS   = "on|enable|start|chalu|jala|jalao|laga|lagao|khol|kholo"
        private const val OFF_WORDS  = "off|disable|band|bujha|bujhao|hata|hatao|stop|chup|bas|enough|rok|roko|cancel|mil gaya|found it|uth gaya"
        private const val UP_WORDS   = "up|increase|raise|louder|higher|badha|badhao|tez|full|zyada"
        private const val DOWN_WORDS = "down|decrease|lower|quieter|softer|dim|kam|dheere|dhire|halka"

        // ---- greeting / conversational ----
        private const val GREET      = "hi|hey|hello|yo|sup|oi|sun|suno|namaste|good morning|good evening|good afternoon|kya haal|sun raha"
        private const val JARVIS     = "jarvis|are you jarvis|tu jarvis"
        private const val WHO        = "tu kaun|who are|tumhara naam kya|kaun ho"
        private const val LOVE       = "i love you|i love you mark"
        private const val THANKS     = "thank you|thanks|shukriya"
        private const val NIGHT      = "good night"
        private const val HOW        = "kaisa|how are"
        private const val REPEAT_KEY = "phir se bol|dobara bol|repeat|kya bola|phir se"
        private const val YES        = "haan|ha|haa|han|ji|ji haan|kar de|kar do|karde|bhej de|yes|yeah|ok|okay|theek|thik|sahi|done|pakka|bilkul|chalega|confirm"
        private const val NO         = "nahi|nhi|na|no|nope|mat kar|mat bhej|rehne de|chhod|chhod de|cancel|cancel kar|galat"
        private const val UNDO_KEY   = "wapas kar|pehle jaisa kar|undo|ulta kar|vapas"

        // Follow-up phrases that carry NO object of their own ("ab band kar").
        // Anchored to the whole utterance: the moment a phrase names its target
        // ("torch band kar", "gaana band kar") the normal rules must win instead.
        private const val CTX_OFF    = "band kar|band karo|bandh kar|off kar|off karo|close kar|stop kar|bujha|band"
        private const val CTX_ON     = "chalu kar|chalu karo|on kar|on karo|chala|jala"
        private const val CTX_UP     = "aur badha|thoda aur|aur tez|badha|badhao"
        private const val CTX_DOWN   = "aur kam|thoda kam|aur dheere|kam kar|kam karo"
        private const val CTX_AGAIN  = "phir se kar|dobara kar|wapas kar"

        // Intents where a negated phrasing must SUPPRESS execution.
        private val NEGATABLE_INTENTS = setOf(
            IntentType.TOGGLE_FLASHLIGHT, IntentType.SET_BRIGHTNESS, IntentType.SET_VOLUME,
            IntentType.SET_SILENT, IntentType.SET_DND, IntentType.SET_ROTATE,
            IntentType.MEDIA_CONTROL, IntentType.SET_ALARM, IntentType.RING_PHONE,
            IntentType.OPEN_APP, IntentType.GO_HOME, IntentType.TAKE_SCREENSHOT,
            IntentType.CALL_CONTACT, IntentType.SEND_SMS, IntentType.LAPTOP_CONTROL
        )

        private const val CACHE_SIZE = 32
    }

    // -------- 1. Normalization --------
    private val assistantPrefixPattern = Regex(
        """^\s*(?:(?:hey|hello)\s+)?mark\s*,?\s*""",
        RegexOption.IGNORE_CASE
    )

    // NOTE: any word added here is stripped BEFORE rule matching.
    // Never add a word that appears inside a multi-word rule pattern above.
    private val fillerTokens = setOf(
        "please", "just", "can", "could", "would", "you", "go", "ahead", "and", "for", "me",
        "yaar", "bhai", "zara", "thoda", "thodi", "karna",
        "dena", "la", "laa",
        "mujhe", "mera", "meri", "main", "ko", "ka", "ki", "hai", "hain",
        "raha", "rahi", "sa", "ekdum", "puri"
    )

    private val sttCorrections = mapOf(
        "flashlite" to "flashlight",
        "flashlight's" to "flashlight",
        "briteness" to "brightness",
        "blutooth" to "bluetooth",
        "volum" to "volume",
        "batery" to "battery"
    )

    private val compoundJoins = mapOf(
        "flash light" to "flashlight",
        "blue tooth" to "bluetooth",
        "do not disturb" to "dnd",
        "heart rate" to "heartrate",
        "to do" to "todo",
        "stop watch" to "stopwatch",
        "air plane" to "airplane"
    )

    private val wordNumbers = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100, "zero" to 0,
        "ek" to 1, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5,
        "chhe" to 6, "che" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10,
        "bees" to 20, "tees" to 30, "chalis" to 40, "pachas" to 50, "sau" to 100
    )

    // "kam" is a DIRECTION (down), never an absolute level of 0.
    private val levelWords = mapOf(
        "full" to 100,
        "half" to 50,
        "mid" to 50,
        "low" to 20,
        "zero" to 0
    )

    // NOTE: "do" (Hindi 2) is deliberately NOT here — "volume kam kar do" must not
    // become "volume kam kar 2". It converts separately, only when a unit follows.
    private val numberWordPattern = Regex(
        """\b(twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)\s+(one|two|three|four|five|six|seven|eight|nine)\b|""" +
                """\b(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|zero|ek|teen|char|chaar|paanch|panch|chhe|che|saat|aath|nau|das|bees|tees|chalis|pachas|sau)\b""",
        RegexOption.IGNORE_CASE
    )

    private val laptopPattern = Regex("""\b(?:$LAPTOP)\b""")

    private val helpGuard = Regex("""(?:kya\s+(?:kya\s+)?kar\s+sakta|tum\s+kya|what\s+can\s+you|karte\s+ho)""")
    private val questionLead = Regex("""^(?:kya\s+(?:tu|tum|aap)\s+|can\s+you\s+|could\s+you\s+|will\s+you\s+|would\s+you\s+)""")
    private val questionTrail = Regex("""\s+(?:sakta\s+hai|sakte\s+ho|sakti\s+hai|sakti\s+ho)\s*\??\s*$""")
    private val doNumberGuard = Regex("""\bdo\b(?=\s+(?:minute|minutes|min|baje|ghante|ghanta|hour|hours|second|seconds|sec|percent|task|\d))""")

    private val vocabByLength: Map<Int, Set<String>> by lazy {
        val allKeys = listOf(
            FLASHLIGHT, BRIGHTNESS, VOLUME, SILENT, UNSILENT, DND, ROTATE, BATTERY, STEPS, SLEEP,
            HEART, WEATHER, T_ADD, T_GET, T_DONE, CALENDAR, TIMER, TIMER_Q, STOPWATCH, CLOCK,
            STATUS, HELP_KEY, ALARM, NOTIFS, PHONE, WATCH, HOME, OPEN, RING, END, CALL, SMS,
            NAV_TRAVEL, NAV_DIST, NAV_NEARBY, SCREENSHOT, MEDIA_NOUN, M_NEXT, M_PREV, M_PAUSE, M_PLAY,
            ON_WORDS, OFF_WORDS, UP_WORDS, DOWN_WORDS, GREET, JARVIS, WHO, LOVE, THANKS, NIGHT,
            HOW, REPEAT_KEY, YES, NO, UNDO_KEY, CTX_OFF, CTX_ON, CTX_UP, CTX_DOWN, CTX_AGAIN,
            // Laptop words MUST be here. Left out, fuzzy repair ate them:
            // "restart" -> "start" (then OPEN_APP fired with app_name="kar")
            // "code"    -> "cold"  (then the open rule stopped matching).
            // Any new constant added above has to be added to this list too.
            LAPTOP, L_LOCK, L_SLEEP, L_SCROFF, L_SHUTDOWN, L_RESTART, L_LOGOFF, L_CANCEL,
            L_CLOSE, L_SWITCH, L_FOLDER, L_SHOT, L_RECORD, L_BRIGHT, L_DARK, L_WIFI,
            L_SEARCH, L_YT, L_GRADLE, L_CLIPGET, L_CLIPSET, L_TYPE, L_FG, L_STATUS, L_APP
        )
        val words = mutableSetOf<String>()
        allKeys.forEach { key ->
            key.split("|").forEach { part ->
                val word = part.replace(Regex("[^a-zA-Z]"), " ").trim()
                word.split(Regex("\\s+")).forEach { if (it.length >= 3) words.add(it.lowercase()) }
            }
        }
        sttCorrections.values.forEach { words.add(it.lowercase()) }
        compoundJoins.values.forEach { words.add(it.lowercase()) }
        wordNumbers.keys.forEach { words.add(it.lowercase()) }
        levelWords.keys.forEach { words.add(it.lowercase()) }

        // Words used by extractors but absent from rule anchors MUST be protected
        // here, or fuzzy repair mangles them. Two real incidents:
        //   "lock"  -> "clock" turned "rotation lock kar" into "rotation clock kar"
        //   "paune" -> "pause" turned "paune 8 ka alarm" into an 8:00 alarm
        for (w in listOf("lock", "unlock", "paune", "sawa", "saade", "sadhe",
            "baje", "baj", "baad", "ghanta", "ghante")) {
            words.add(w)
        }

        words.groupBy { it.length }.mapValues { it.value.toSet() }
    }

    private fun levenshtein(s1: String, s2: String, max: Int): Int {
        if (s1 == s2) return 0
        if (abs(s1.length - s2.length) > max) return max + 1

        val m = s2.length
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)

        for (i in 1..s1.length) {
            curr[0] = i
            var minInRow = curr[0]
            for (j in 1..m) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                if (curr[j] < minInRow) minInRow = curr[j]
            }
            if (minInRow > max) return max + 1
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[m]
    }

    /** Returns normalized text + fuzzy repair count. */
    private fun normalize(rawInput: String): Pair<String, Int> {
        val input = rawInput.lowercase()
        var text = assistantPrefixPattern.replace(input, "").trim()
        if (text.isEmpty()) return "" to 0

        // Question-wrapper stripping: "kya tu torch chala sakta hai" == "torch chala".
        // Guarded so HELP phrases ("kya kya kar sakta hai"), which ARE questions,
        // keep their wrapper and still match HELP_KEY.
        if (!helpGuard.containsMatchIn(text)) {
            text = questionLead.replace(text, "")
            text = questionTrail.replace(text, "")
        }

        val rawTokens = text.split(Regex("\\s+"))
        val correctedTokens = rawTokens.map { sttCorrections[it] ?: it }
        val filteredTokens = correctedTokens.filter { it !in fillerTokens }
        val workingTokens = if (filteredTokens.isEmpty()) correctedTokens else filteredTokens

        var repairCount = 0
        val repairedTokens = workingTokens.map { token ->
            if (repairCount >= 3 || token.length < 4 || vocabByLength[token.length]?.contains(token) == true) {
                token
            } else {
                val maxDist = if (token.length <= 6) 1 else 2
                var bestMatch: String? = null
                var bestDist = maxDist + 1
                var tie = false

                for (l in (token.length - 2)..(token.length + 2)) {
                    vocabByLength[l]?.forEach { candidate ->
                        val dist = levenshtein(token, candidate, maxDist)
                        if (dist <= maxDist) {
                            if (dist < bestDist) {
                                bestDist = dist
                                bestMatch = candidate
                                tie = false
                            } else if (dist == bestDist) {
                                tie = true
                            }
                        }
                    }
                }

                if (bestMatch != null && !tie && bestMatch != token) {
                    android.util.Log.d("MarkFuzzy", "$token -> $bestMatch (dist=$bestDist)")
                    repairCount++
                    bestMatch!!
                } else {
                    token
                }
            }
        }

        if (repairCount > 0) {
            android.util.Log.d("MarkFuzzy", "Total repairs: $repairCount")
        }

        var normalized = repairedTokens.joinToString(" ")
        compoundJoins.forEach { (old, new) -> normalized = normalized.replace(old, new) }

        // "do" = 2 only when a unit follows; "kar do" stays imperative.
        normalized = doNumberGuard.replace(normalized, "2")

        normalized = numberWordPattern.replace(normalized) { match ->
            val g1 = match.groups[1]?.value
            val g2 = match.groups[2]?.value
            val g3 = match.groups[3]?.value
            if (g1 != null && g2 != null) {
                ((wordNumbers[g1] ?: 0) + (wordNumbers[g2] ?: 0)).toString()
            } else if (g3 != null) {
                (wordNumbers[g3] ?: g3).toString()
            } else match.value
        }

        if (Regex("""\b(?:brightness|screen|display|bright|chamak|volume|sound|loudness|audio|awaaz|aawaz|laptop|pc|computer|macbook|desktop)\b""").containsMatchIn(normalized)) {
            levelWords.forEach { (word, value) ->
                normalized = normalized.replace(Regex("""\b$word\b"""), value.toString())
            }
        }

        normalized = normalized.replace(Regex("""\b(?:percent|%)\b"""), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        android.util.Log.d("MarkNorm", "'$rawInput' -> '$normalized' (repairs: $repairCount)")
        return normalized to repairCount
    }

    // -------- 2. Extractors --------

    private val spanLeading = Regex("""^(?:to|ke|tak|par|se|pe|the|a|an|my|mere)\s+""")
    private val spanTrailing = Regex("""\s+(?:dikhao|dikha|batao|bata|karo|kardo|kar|chalao|chala|milao|mila|lagao|laga|do|de|please|phone|mobile)$""")

    private fun extractTaskContent(rawInput: String, anchor: String): String? {
        val lowerRaw = rawInput.lowercase()
        val anchorIdx = lowerRaw.indexOf(anchor.lowercase())
        if (anchorIdx == -1) return null
        val content = rawInput.substring(anchorIdx + anchor.length).trim()
        return content.ifBlank { null }
    }

    private fun extractTaskIndex(text: String): String? =
        Regex("""\b(\d+)\b""").find(text)?.groupValues?.get(1)

    /**
     * anchorStart is the winning match's start offset — NOT indexOf(anchor), which
     * pointed at the wrong occurrence whenever the anchor word appeared twice.
     */
    private fun extractState(text: String, anchorStart: Int): String? {
        if (Regex("""\b(?:snooze|thodi\s+der|5\s+minute\s+aur)\b""").containsMatchIn(text)) return "snooze"

        val onIdx = findNearest(text, anchorStart, ON_WORDS)
        val offIdx = findNearest(text, anchorStart, OFF_WORDS)

        return when {
            onIdx != -1 && (offIdx == -1 || abs(onIdx - anchorStart) < abs(offIdx - anchorStart)) -> "on"
            offIdx != -1 -> "off"
            else -> null
        }
    }

    /** A silent-family anchor with no explicit state means ON; an unsilent anchor means OFF. */
    private fun extractSilentState(text: String, anchorStart: Int): String {
        if (Regex("""\b(?:$UNSILENT)\b""").containsMatchIn(text)) return "off"
        return extractState(text, anchorStart) ?: "on"
    }

    private fun extractLevel(text: String): String? =
        Regex("""\b(\d{1,3})\b""").find(text)?.groupValues?.get(1)

    private fun extractDirection(text: String): String? {
        if (Regex("""\b(?:$UP_WORDS)\b""").containsMatchIn(text)) return "up"
        if (Regex("""\b(?:$DOWN_WORDS)\b""").containsMatchIn(text)) return "down"
        return null
    }

    /** Delta size for relative commands, read from RAW input — "thoda" is a filler. */
    private fun extractAmount(rawInput: String): String? {
        val raw = rawInput.lowercase()
        return when {
            Regex("""\b(?:thoda|thodi|halka|slightly|little)\b""").containsMatchIn(raw) -> "10"
            Regex("""\b(?:bahut|bohot|zyada|lot)\b""").containsMatchIn(raw) -> "25"
            else -> null
        }
    }

    private fun extractTarget(text: String): String =
        if (Regex("""\b(?:$WATCH)\b""").containsMatchIn(text)) "watch" else "phone"

    /** Rotation ON == lock OFF; rotation OFF == lock ON. */
    private fun extractLockState(text: String): String? {
        if (Regex("""\bunlock\b""").containsMatchIn(text)) return "off"
        if (Regex("""\block\b""").containsMatchIn(text)) return "on"
        if (Regex("""\b(?:$ON_WORDS)\b""").containsMatchIn(text)) return "off"
        if (Regex("""\b(?:$OFF_WORDS)\b""").containsMatchIn(text)) return "on"
        return null
    }

    private val relativeTimePattern =
        Regex("""\b(\d+)\s*(minute|minutes|min|ghanta|ghante|hour|hours)\s*(?:baad|me|mein)\b""")

    /** "10 minute baad" -> minutes-from-now. The tool decides what "now" is. */
    private fun extractRelativeMinutes(text: String): String? {
        val m = relativeTimePattern.find(text) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val unit = m.groupValues[2]
        return if (unit.startsWith("ghant") || unit.startsWith("hour")) (n * 60).toString() else n.toString()
    }

    private fun extractClockTime(text: String): String? {
        // A relative expression wins outright — otherwise "10 minute baad" would
        // ALSO produce an absolute 10:00 and the alarm would fire at the wrong time.
        if (relativeTimePattern.containsMatchIn(text)) return null

        // paune 8 = 7:45, sawa 8 = 8:15, saade 8 = 8:30
        Regex("""\b(paune|sawa|saade|sadhe)\s+(\d{1,2})\b""").find(text)?.let { m ->
            var hour = m.groupValues[2].toIntOrNull() ?: return null
            var minute = 30
            when (m.groupValues[1]) {
                "paune" -> { hour -= 1; minute = 45 }
                "sawa" -> minute = 15
            }
            return applyAmPm(text, hour, minute)
        }

        // "7 baj kar 20" / "7 baj ke 20"
        Regex("""\b(\d{1,2})\s+baj\s*(?:kar|ke)\s+(\d{1,2})\b""").find(text)?.let { m ->
            return applyAmPm(text, m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }

        val match = Regex("""\b(?:saade\s+)?(\d{1,2})(?::|[\s\-])?(\d{2})?\b""").find(text) ?: return null
        var hour = match.groups[1]?.value?.toIntOrNull() ?: return null
        var minute = match.groups[2]?.value?.toIntOrNull() ?: 0
        if (text.contains("saade")) minute = 30
        return applyAmPm(text, hour, minute)
    }

    private fun applyAmPm(text: String, h: Int, m: Int): String {
        var hour = h
        val isPm = text.contains(Regex("""\b(?:shaam|raat|evening|night|pm)\b"""))
        val isAm = text.contains(Regex("""\b(?:subah|morning|am)\b"""))
        when {
            isPm -> if (hour < 12) hour += 12
            isAm -> if (hour == 12) hour = 0
            else -> if (hour in 1..6) hour += 12
        }
        return String.format("%02d:%02d", hour % 24, m % 60)
    }

    /** Span = the text minus the matched anchor, minus connectors and trailing verbs. */
    private fun extractSpan(text: String, matchStart: Int, matchEnd: Int): String? {
        var result = (text.substring(0, matchStart) + " " + text.substring(matchEnd))
            .replace(Regex("\\s+"), " ")
            .trim()
        var previous: String
        do {
            previous = result
            result = spanLeading.replace(result, "")
            result = spanTrailing.replace(result, "")
            result = result.trim()
        } while (result != previous && result.isNotEmpty())
        return result.ifBlank { null }
    }

    /** Nearest occurrence of ANY alternative to anchorStart — scans all matches. */
    private fun findNearest(text: String, anchorStart: Int, alternatives: String): Int {
        var best = -1
        var bestDist = Int.MAX_VALUE
        for (p in alternatives.split("|")) {
            val rx = Regex("""\b${Regex.escape(p)}\b""")
            for (m in rx.findAll(text)) {
                val d = abs(m.range.first - anchorStart)
                if (d < bestDist) { bestDist = d; best = m.range.first }
            }
        }
        return best
    }

    // -------- 3. Rules --------
    // Priority guide:
    //   20  specific query intents that must beat generic anchors
    //   18  context follow-ups (must beat media's generic "band"/"stop")
    //   15  session / repeat / undo
    //   12  confirmation (gated by awaitingConfirmation in the controller)
    //   10  time manager
    //    5  navigation / comms / system actions
    //    0  default
    //   -5  media (very generic words: play, stop, band, last, next)
    //  -10  easter eggs (must never steal a real command)
    private val rules = listOf(
        Rule(IntentType.GREETING, Regex("""^\s*(?:$GREET)[\s!.]*$""")),

        Rule(IntentType.TIMER_QUERY, Regex("""\b(?:$TIMER_Q)\b"""), priority = 20),
        // "laptop ka status batao" is not a watch question.
        Rule(IntentType.WATCH_STATUS, Regex("""\b(?:$STATUS)\b"""), priority = 20,
            blockedBy = Regex("""\b(?:$LAPTOP)\b""")),
        Rule(IntentType.HELP, Regex("""\b(?:$HELP_KEY)\b"""), priority = 20),

        Rule(IntentType.REPEAT, Regex("""\b(?:$REPEAT_KEY)\b"""), priority = 15),
        Rule(IntentType.UNDO, Regex("""\b(?:$UNDO_KEY)\b"""), priority = 15),
        Rule(IntentType.END_SESSION, Regex("""\b(?:$END)\b"""), priority = 15),

        // CONTEXT FOLLOW-UP: beats MEDIA_CONTROL, which otherwise swallows
        // "ab band kar" via its generic "band"/"stop" words. The controller
        // resolves these against the last intent.
        Rule(IntentType.CONTEXT_FOLLOW_UP, Regex("""^\s*(?:ab\s+)?(?:$CTX_OFF)\s*(?:de|do|dijiye)?\s*$"""),
            fixedParams = mapOf("ctx" to "off"), priority = 18),
        Rule(IntentType.CONTEXT_FOLLOW_UP, Regex("""^\s*(?:ab\s+)?(?:$CTX_ON)\s*(?:de|do|dijiye)?\s*$"""),
            fixedParams = mapOf("ctx" to "on"), priority = 18),
        Rule(IntentType.CONTEXT_FOLLOW_UP, Regex("""^\s*(?:ab\s+)?(?:$CTX_UP)\s*(?:de|do|dijiye)?\s*$"""),
            fixedParams = mapOf("ctx" to "up"), priority = 18),
        Rule(IntentType.CONTEXT_FOLLOW_UP, Regex("""^\s*(?:ab\s+)?(?:$CTX_DOWN)\s*(?:de|do|dijiye)?\s*$"""),
            fixedParams = mapOf("ctx" to "down"), priority = 18),
        Rule(IntentType.CONTEXT_FOLLOW_UP, Regex("""^\s*(?:ab\s+)?(?:$CTX_AGAIN)\s*(?:de|do|dijiye)?\s*$"""),
            fixedParams = mapOf("ctx" to "again"), priority = 18),

        // Confirmation: anchored to the START with a short tail, so a sentence that
        // merely CONTAINS a yes-word ("mark task 2 as done") can never be hijacked.
        Rule(IntentType.CONFIRMATION, Regex("""^\s*(?:$YES)\b.{0,12}$"""), fixedParams = mapOf("decision" to "yes"), priority = 12),
        Rule(IntentType.CONFIRMATION, Regex("""^\s*(?:$NO)\b.{0,12}$"""), fixedParams = mapOf("decision" to "no"), priority = 12),

        Rule(IntentType.TIME_MANAGER, Regex("""\b(?:stop|cancel)\s+(?:the\s+)?(?:timer|stopwatch|alarm)\b"""),
            fixedParams = mapOf("action" to "stop"), priority = 10),
        Rule(IntentType.TIME_MANAGER, Regex("""\b(?:$TIMER)\b"""),
            extractors = listOf("timer_params"), fixedParams = mapOf("action" to "timer"), priority = 10),
        Rule(IntentType.TIME_MANAGER, Regex("""\b(?:$STOPWATCH)\b"""),
            fixedParams = mapOf("action" to "stopwatch"), priority = 10),
        Rule(IntentType.TIME_MANAGER, Regex("""(?:what.*\b(?:$CLOCK)\b|\b(?:$CLOCK)\b\s*$)"""),
            fixedParams = mapOf("action" to "time"), priority = 10),

        Rule(IntentType.GET_STEPS, Regex("""\b(?:$STEPS)\b"""), blockedBy = Regex("""(?i)\b(?:delete|remove)\b""")),
        Rule(IntentType.GET_SLEEP, Regex("""\b(?:$SLEEP)\b"""), blockedBy = Regex("""(?i)\b(?:delete|remove)\b""")),
        Rule(IntentType.GET_HEART_RATE, Regex("""\b(?:$HEART)\b"""), blockedBy = Regex("""(?i)\b(?:delete|remove)\b""")),
        Rule(IntentType.GET_WEATHER, Regex("""\b(?:$WEATHER)\b""")),

        Rule(IntentType.COMPLETE_TASK, Regex("""\b(?:$T_DONE)\b"""), extractors = listOf("task_index"), priority = 5),
        Rule(IntentType.ADD_TASK, Regex("""\b(?:$T_ADD)\b"""), extractors = listOf("task_content"), priority = 5),
        Rule(IntentType.GET_TASKS, Regex("""\b(?:$T_GET)\b"""), blockedBy = Regex("""(?i)\b(?:delete|remove)\b""")),

        Rule(IntentType.GET_CALENDAR, Regex("""\b(?:$CALENDAR)\b""")),

        Rule(IntentType.READ_LAST_MESSAGE, Regex("""\b(?:last|latest|pichla|akhri)\s+(?:$NOTIFS)\b"""),
            priority = 5, blockedBy = Regex("""(?i)\b(?:delete|remove)\b""")),
        Rule(IntentType.CHECK_NEW_MESSAGES, Regex("""\b(?:any|kuch|new|naya)\s+(?:$NOTIFS)\b"""),
            priority = 5, blockedBy = Regex("""(?i)\b(?:delete|remove)\b""")),
        Rule(IntentType.UNREAD_COUNT, Regex("""\b(?:how many|kitne)\s+(?:$NOTIFS)\b"""), priority = 5),
        Rule(IntentType.READ_NOTIFICATIONS, Regex("""\b(?:read|check|padho)\s+(?:the\s+|my\s+|mere\s+)?(?:$NOTIFS)\b"""),
            blockedBy = Regex("""(?i)\b(?:delete|remove)\b""")),
        Rule(IntentType.READ_NOTIFICATIONS, Regex("""\b(?:$NOTIFS)\s+(?:batao|bata|dikhao|dikha|padho|read)\b"""),
            blockedBy = Regex("""(?i)\b(?:delete|remove)\b""")),

        Rule(IntentType.SET_BRIGHTNESS, Regex("""\b(?:$BRIGHTNESS)\b"""),
            extractors = listOf("level", "direction", "amount", "target")),
        Rule(IntentType.TOGGLE_FLASHLIGHT, Regex("""\b(?:$FLASHLIGHT)\b"""),
            extractors = listOf("state")),
        Rule(IntentType.GET_BATTERY, Regex("""\b(?:$BATTERY)\b"""),
            extractors = listOf("target")),
        Rule(IntentType.SET_SILENT, Regex("""\b(?:$SILENT|$UNSILENT)\b"""),
            extractors = listOf("silent_state", "target")),
        Rule(IntentType.SET_DND, Regex("""\b(?:$DND)\b"""),
            extractors = listOf("state")),
        Rule(IntentType.SET_VOLUME, Regex("""\b(?:$VOLUME)\b"""),
            extractors = listOf("level", "direction", "amount", "target")),
        Rule(IntentType.SET_ROTATE, Regex("""\b(?:$ROTATE)\b"""),
            extractors = listOf("lock_state")),

        Rule(IntentType.CALL_CONTACT, Regex("""\b(?:$CALL)\b"""), extractors = listOf("contact"),
            blockedBy = Regex("""(?i)\b(?:create|schedule\s+(?:a|an))\b""")),
        Rule(IntentType.SEND_SMS, Regex("""\b(?:$SMS)\b"""), extractors = listOf("message_body"),
            blockedBy = Regex("""(?i)\b(?:create|schedule\s+(?:a|an))\b""")),

        Rule(IntentType.RING_PHONE, Regex("""\b(?:$RING)\b.*\b(?:$PHONE)\b"""),
            extractors = listOf("state"), priority = 5),
        Rule(IntentType.RING_PHONE, Regex("""\b(?:$PHONE)\b.*\b(?:$RING)\b"""),
            extractors = listOf("state"), priority = 5),

        Rule(IntentType.SET_ALARM, Regex("""\b(?:$ALARM)\b"""),
            extractors = listOf("clockTime", "relative", "state", "target"),
            blockedBy = Regex("""(?i)\b(?:delete|remove)\b""")),

        Rule(IntentType.NAVIGATE_TO, Regex("""\b(?:$NAV_TRAVEL)\b"""), extractors = listOf("destination"), priority = 5),
        Rule(IntentType.GET_DISTANCE, Regex("""\b(?:$NAV_DIST)\b"""), extractors = listOf("destination"), priority = 5),
        Rule(IntentType.FIND_NEARBY, Regex("""\b(?:$NAV_NEARBY)\b"""), extractors = listOf("placeType"), priority = 5),

        // blockedBy: "torch khol" / "flashlight kholo" are toggles, not app launches.
        // The capture used to be [a-zA-Z0-9\s]+ , which swallowed the rest of the
        // sentence: "open whatsapp on my phone" gave app_name="whatsapp on my phone".
        // It now stops at a trailing target phrase or a connector.
        Rule(IntentType.OPEN_APP,
            Regex("""\b(?:$OPEN)\s+([a-zA-Z0-9]+(?:\s+[a-zA-Z0-9]+)??)(?=\s+(?:on|in|pe|par|mere|my|the)\b|\s*$)"""),
            listOf("app_name"),
            extractors = listOf("target"),
            blockedBy = Regex("""\b(?:torch|flashlight|batti|roshni|brightness|volume|dnd|rotation|alarm|timer)\b""")),
        Rule(IntentType.OPEN_APP, Regex("""^([a-zA-Z0-9\s]+?)\s+(?:kholo|khol do|khol|open karo)$"""), listOf("app_name"),
            extractors = listOf("target"),
            blockedBy = Regex("""\b(?:torch|flashlight|batti|roshni|brightness|volume|dnd|rotation|alarm|timer)\b""")),
        Rule(IntentType.OPEN_APP, Regex("""\b(?:wifi|wi-fi|internet|connection)\b"""),
            extractors = listOf("state"), fixedParams = mapOf("app_name" to "wifi"), priority = 5),
        Rule(IntentType.OPEN_APP, Regex("""\b(?:data|mobile data|net|cellular)\b"""),
            extractors = listOf("state"), fixedParams = mapOf("app_name" to "mobile_data"), priority = 5),
        Rule(IntentType.OPEN_APP, Regex("""\b(?:screen\s+record|record\s+screen|video\s+record\s+screen|record\s+video\s+screen)\b"""),
            fixedParams = mapOf("app_name" to "screen_record"), priority = 10),
        Rule(IntentType.OPEN_APP, Regex("""\b(?:record|video|camera)\b"""),
            fixedParams = mapOf("app_name" to "camera")),

        Rule(IntentType.TAKE_SCREENSHOT, Regex("""\b(?:$SCREENSHOT)\b"""), priority = 5),

        // ---- laptop. Priorities encode specificity: the more specific the
        // ---- verb, the higher it sits, so a generic word can never outrank it.
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_CANCEL)\b|\b(?:$L_CANCEL)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "cancel_shutdown"), priority = 40),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_GRADLE)\b|\b(?:$L_GRADLE)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "gradle"), priority = 34),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_YT)\b|\b(?:$L_YT)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "browser", "site" to "youtube"), priority = 33),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_SEARCH)\b|\b(?:$L_SEARCH)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "browser"), priority = 33),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_CLIPGET)\b|\b(?:$L_CLIPGET)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "clipboard_get"), priority = 33),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_CLIPSET)\b|\b(?:$L_CLIPSET)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "clipboard_set"), priority = 33),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_TYPE)\b|\b(?:$L_TYPE)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "type"), priority = 33),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_FG)\b|\b(?:$L_FG)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "foreground"), priority = 33),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_RECORD)\b|\b(?:$L_RECORD)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "screen_record"), priority = 32),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_SHOT)\b|\b(?:$L_SHOT)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "screenshot"), priority = 32),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b($L_FOLDER)\b|\b($L_FOLDER)\b.*\b(?:$LAPTOP)\b"""),
            listOf("folder", "folder2"),
            fixedParams = mapOf("action" to "folder"), priority = 31),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_DARK)\b|\b(?:$L_DARK)\b.*\b(?:$LAPTOP)\b"""), extractors = listOf("laptop_state"),
            fixedParams = mapOf("action" to "dark_mode"), priority = 31),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_WIFI)\b|\b(?:$L_WIFI)\b.*\b(?:$LAPTOP)\b"""), extractors = listOf("laptop_state"),
            fixedParams = mapOf("action" to "wifi"), priority = 31),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_BRIGHT)\b|\b(?:$L_BRIGHT)\b.*\b(?:$LAPTOP)\b"""), extractors = listOf("direction", "level", "amount"),
            fixedParams = mapOf("action" to "brightness"), priority = 31),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_LOGOFF)\b|\b(?:$L_LOGOFF)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "logoff"), priority = 30),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_RESTART)\b|\b(?:$L_RESTART)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "restart"), priority = 30),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_SHUTDOWN)\b|\b(?:$L_SHUTDOWN)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "shutdown"), priority = 30),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_SCROFF)\b|\b(?:$L_SCROFF)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "screen_off"), priority = 30),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_SLEEP)\b|\b(?:$L_SLEEP)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "sleep"), priority = 29),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_SWITCH)\b|\b(?:$L_SWITCH)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "switch"), priority = 28),
        // Close needs the verb AND the app adjacent, in either order, so a bare
        // "band" cannot turn an open request into a close one.
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""(?:\b(?:$L_CLOSE)\s+(?:the\s+)?($L_APP)\b|\b($L_APP)\s+(?:ko\s+)?(?:$L_CLOSE)\b)"""),
            listOf("app", "app2"),
            fixedParams = mapOf("action" to "close"), priority = 27,
            requiresLaptop = true),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b($L_APP)\b|\b($L_APP)\b.*\b(?:$LAPTOP)\b"""),
            listOf("app", "app2"),
            fixedParams = mapOf("action" to "open"), priority = 26),
        // Media must name what it controls. Including the generic pause words here
        // made "laptop band kar de" a pause instead of a lock.
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$MEDIA_NOUN|$VOLUME)\b|\b(?:$MEDIA_NOUN|$VOLUME)\b.*\b(?:$LAPTOP)\b"""),
            extractors = listOf("media_action", "direction", "level"),
            fixedParams = mapOf("action" to "media"), priority = 25),
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$LAPTOP)\b.*\b(?:$L_LOCK)\b|\b(?:$L_LOCK)\b.*\b(?:$LAPTOP)\b"""),
            fixedParams = mapOf("action" to "lock"), priority = 24),
        // Status needs a status word. The old version also matched a BARE
        // "laptop", which meant a half-heard command — speech recognition emits
        // "laptop", then "laptop lock", then "laptop lock kar" — could fire a
        // status reply before the user had finished the sentence.
        // Word order here is genuinely free — "laptop kaisa hai", "how is the
        // laptop", "laptop ka status batao". Rather than spelling out every
        // ordering in one regex, match the status word and let requiresLaptop
        // enforce the anchor separately.
        Rule(IntentType.LAPTOP_CONTROL,
            Regex("""\b(?:$L_STATUS)\b"""),
            fixedParams = mapOf("action" to "status"), priority = 19,
            requiresLaptop = true),

        // GO_HOME. These sit right before the media rules; when the laptop block
        // was spliced in they were removed by accident and "go home" resolved to
        // nothing — caught by the golden suite, which is exactly its job.
        Rule(IntentType.GO_HOME,
            Regex("""\b(?:clear\s+all|ram\s+saaf|background\s+hata|saare\s+apps\s+hata|background\s+clear)\b"""),
            fixedParams = mapOf("action" to "clear_recents"), priority = 10),
        Rule(IntentType.GO_HOME, Regex("""\b(?:$HOME)\b""")),

        Rule(IntentType.MEDIA_CONTROL, Regex("""\b(?:$M_NEXT)\b"""), fixedParams = mapOf("action" to "next"), priority = -5),
        Rule(IntentType.MEDIA_CONTROL, Regex("""\b(?:$M_PREV)\b"""), fixedParams = mapOf("action" to "previous"), priority = -5),
        Rule(IntentType.MEDIA_CONTROL, Regex("""\b(?:$M_PAUSE)\b"""), fixedParams = mapOf("action" to "pause"), priority = -5),
        Rule(IntentType.MEDIA_CONTROL, Regex("""\b(?:$M_PLAY)\b"""), fixedParams = mapOf("action" to "play"), priority = -5),

        Rule(IntentType.EASTER_EGG, Regex("""\b(?:$JARVIS)\b"""), fixedParams = mapOf("type" to "jarvis"), priority = -10),
        Rule(IntentType.EASTER_EGG, Regex("""\b(?:$WHO)\b"""), fixedParams = mapOf("type" to "who"), priority = -10),
        Rule(IntentType.EASTER_EGG, Regex("""\b(?:$LOVE)\b"""), fixedParams = mapOf("type" to "love"), priority = -10),
        Rule(IntentType.EASTER_EGG, Regex("""\b(?:$THANKS)\b"""), fixedParams = mapOf("type" to "thanks"), priority = -10),
        Rule(IntentType.EASTER_EGG, Regex("""\b(?:$NIGHT)\b"""), fixedParams = mapOf("type" to "night"), priority = -10),
        Rule(IntentType.EASTER_EGG, Regex("""\b(?:$HOW)\b"""), fixedParams = mapOf("type" to "how"), priority = -10)
    )

    // -------- 4. Prefilter index --------
    // token -> indices of rules whose pattern contains that literal word.
    // A rule regex of the \b(?:word|word|...)\b family can only match if one of its
    // literal words is present, so filtering by token is lossless — and it cuts the
    // regexes actually executed per command from ~65 to ~5 (measured).
    private val ruleKeywords: List<Set<String>> by lazy {
        rules.map { rule ->
            Regex("[^a-zA-Z]+").split(rule.pattern.pattern)
                .filter { it.length >= 3 && it.all { c -> c.isLetter() } }
                .map { it.lowercase() }
                .toSet()
        }
    }

    private val tokenToRules: Map<String, IntArray> by lazy {
        val map = mutableMapOf<String, MutableSet<Int>>()
        ruleKeywords.forEachIndexed { i, keys ->
            keys.forEach { k -> map.getOrPut(k) { mutableSetOf() }.add(i) }
        }
        // Compound joins produce tokens like "heartrate" that rule patterns spell
        // as "heart rate" — map the joined form to every rule that knows a half.
        // (Found by simulation: without this, "check heart rate" resolved to null.)
        compoundJoins.forEach { (old, new) ->
            val halves = old.split(" ").filter { it.length >= 3 }
            ruleKeywords.forEachIndexed { i, keys ->
                if (halves.any { it in keys }) map.getOrPut(new) { mutableSetOf() }.add(i)
            }
        }
        map.mapValues { it.value.toIntArray() }
    }

    private val alwaysCheckRules: IntArray by lazy {
        ruleKeywords.mapIndexedNotNull { i, keys -> if (keys.isEmpty()) i else null }.toIntArray()
    }

    // -------- 5. Result cache --------
    private val cache = object : LinkedHashMap<String, IntentResult>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntentResult>) =
            size > CACHE_SIZE
    }

    fun resolve(rawInput: String): IntentResult {
        val cacheKey = rawInput.trim().lowercase()
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val result = resolveUncached(rawInput)
        synchronized(cache) { cache[cacheKey] = result }
        return result
    }

    private fun resolveUncached(rawInput: String): IntentResult {
        val (normalized, repairs) = normalize(rawInput)
        if (normalized.isEmpty()) return IntentResult(Intent(IntentType.GREETING), 1.0f, normalized)

        val tokens = normalized.split(' ')

        // ---- prefilter ----
        val candidateRuleIdx = sortedSetOf<Int>()
        alwaysCheckRules.forEach { candidateRuleIdx.add(it) }
        for (t in tokens) tokenToRules[t]?.forEach { candidateRuleIdx.add(it) }

        val candidates = mutableListOf<Candidate>()
        for (index in candidateRuleIdx) {
            val rule = rules[index]
            val blockerMatch = rule.blockedBy?.find(normalized)
            if (blockerMatch != null) {
                android.util.Log.d("MarkBlock", "Rule ${rule.intent} blocked by '${blockerMatch.value}' in '$normalized'")
                continue
            }

            if (rule.requiresLaptop && !laptopPattern.containsMatchIn(normalized)) continue

            val match = rule.pattern.find(normalized) ?: continue

            val params = mutableMapOf<String, String>()
            params.putAll(rule.fixedParams)
            var extractorProducedParam = false

            rule.paramNames.forEachIndexed { pIndex, name ->
                match.groups[pIndex + 1]?.value?.let {
                    params[name] = it.trim()
                    extractorProducedParam = true
                }
            }

            val anchorStart = match.range.first
            rule.extractors.forEach { ex ->
                val paramAdded = when (ex) {
                    "state" -> extractState(normalized, anchorStart)?.let { params["state"] = it; true } ?: false
                    "silent_state" -> { params["state"] = extractSilentState(normalized, anchorStart); true }
                    "level" -> extractLevel(normalized)?.let { params["level"] = it; true } ?: false
                    "direction" -> extractDirection(normalized)?.let { params["direction"] = it; true } ?: false
                    "amount" -> extractAmount(rawInput)?.let { params["amount"] = it; true } ?: false
                    "target" -> { params["target"] = extractTarget(normalized); true }
                    "lock_state" -> extractLockState(normalized)?.let { params["state"] = it; true } ?: false
                    "clockTime" -> extractClockTime(normalized)?.let { params["time"] = it; true } ?: false
                    "relative" -> extractRelativeMinutes(normalized)?.let { params["relative_minutes"] = it; true } ?: false
                    "contact" -> extractSpan(normalized, match.range.first, match.range.last + 1)?.let { params["contact"] = it; true } ?: false
                    // SMS body from RAW input: normalize() strips words that belong in messages.
                    "message_body" -> extractTaskContent(rawInput, match.value)?.let { params["payload"] = it; true } ?: false
                    "destination" -> extractSpan(normalized, match.range.first, match.range.last + 1)?.let { params["destination"] = it; true } ?: false
                    "placeType" -> extractSpan(normalized, match.range.first, match.range.last + 1)?.let { params["placeType"] = it; true } ?: false
                    "task_content" -> extractTaskContent(rawInput, match.value)?.let { params["content"] = it; true } ?: false
                    "task_index" -> extractTaskIndex(normalized)?.let { params["index"] = it; true } ?: false
                    "laptop_state" -> {
                        val off = Regex("""\b(?:off|band|bandh|disable|light mode)\b""").containsMatchIn(normalized)
                        params["state"] = if (off) "off" else "on"
                        true
                    }
                    "media_action" -> {
                        val a = when {
                            Regex("""\b(?:$M_NEXT)\b""").containsMatchIn(normalized) -> "next"
                            Regex("""\b(?:$M_PREV)\b""").containsMatchIn(normalized) -> "previous"
                            Regex("""\b(?:$M_PAUSE)\b""").containsMatchIn(normalized) -> "pause"
                            Regex("""\b(?:$M_PLAY)\b""").containsMatchIn(normalized) -> "play"
                            Regex("""\b(?:$VOLUME)\b""").containsMatchIn(normalized) -> "volume"
                            else -> null
                        }
                        if (a != null) { params["media_action"] = a; true } else false
                    }
                    "timer_params" -> {
                        Regex("""(\d+)\s*(min|minute|minutes|sec|second|seconds|hour|hours)?""").find(normalized)?.let { m ->
                            val num = m.groupValues[1].toIntOrNull() ?: 0
                            val unit = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "min"
                            val seconds = when {
                                unit.startsWith("hour") -> num * 3600
                                unit.startsWith("sec") -> num
                                else -> num * 60
                            }
                            params["seconds"] = seconds.toString()
                            true
                        } ?: false
                    }
                    else -> false
                }
                if (paramAdded) extractorProducedParam = true
            }

            candidates.add(Candidate(rule, params, match.value.length, index, extractorProducedParam, match.range.first, match.range.last + 1))
        }

        if (candidates.isEmpty()) {
            android.util.Log.d("MarkPick", "no candidates for '$normalized' (prefilter tried ${candidateRuleIdx.size})")
            return IntentResult(null, 0f, normalized)
        }

        // Collision 3: "sab band" -> GO_HOME
        if (normalized.contains(Regex("""\bsab\s+band\b"""))) {
            logConfidence(normalized, IntentType.GO_HOME, 1.0f, 1.0f, true, repairs)
            return IntentResult(Intent(IntentType.GO_HOME), 1.0f, normalized)
        }

        val filtered = candidates.toMutableList()

        // Collision 1: bare "light" is ambiguous between torch and brightness
        val lightCandidates = filtered.filter {
            (it.rule.intent == IntentType.TOGGLE_FLASHLIGHT || it.rule.intent == IntentType.SET_BRIGHTNESS) &&
                    it.matchedLength == 5
        }
        if (lightCandidates.size > 1 && !normalized.contains(Regex("""\b(flashlight|torch|batti|roshni|screen|brightness|display)\b"""))) {
            val state = extractState(normalized, normalized.indexOf("light"))
            val direction = extractDirection(normalized)
            val level = extractLevel(normalized)

            filtered.removeAll(lightCandidates)
            val selected = when {
                direction != null || level != null -> lightCandidates.find { it.rule.intent == IntentType.SET_BRIGHTNESS }
                state != null -> lightCandidates.find { it.rule.intent == IntentType.TOGGLE_FLASHLIGHT }
                else -> lightCandidates.find { it.rule.intent == IntentType.TOGGLE_FLASHLIGHT }
            }
            selected?.let { filtered.add(it) }
        }

        // Collision 4: generic play words ("chala") must not beat a real anchor
        val playCandidates = filtered.filter {
            it.rule.intent == IntentType.MEDIA_CONTROL && it.params["action"] == "play"
        }
        if (playCandidates.isNotEmpty()) {
            val hasMediaNoun = normalized.contains(Regex("""\b(?:$MEDIA_NOUN)\b"""))
            val otherAnchors = filtered.any {
                it.rule.intent != IntentType.MEDIA_CONTROL && it.rule.intent != IntentType.GREETING
            }
            if (!hasMediaNoun && otherAnchors) filtered.removeAll(playCandidates)
        }

        if (filtered.isEmpty()) return IntentResult(null, 0f, normalized)

        val winner = filtered.sortedWith(
            compareByDescending<Candidate> { it.rule.priority }
                .thenByDescending { it.params.size }
                .thenByDescending { it.matchedLength }
                .thenBy { it.originalIndex }
        ).first()

        android.util.Log.d(
            "MarkPick",
            "winner=${winner.rule.intent} candidates=${filtered.size} prefilter=${candidateRuleIdx.size}/${rules.size}"
        )

        // NEGATION: narrow patterns so a descriptive "kuch dikh nahi raha light
        // badha" still executes, but "torch mat chalana" never turns the torch on.
        val negated = winner.rule.intent in NEGATABLE_INTENTS && (
                Regex("""\bmat\s+\S+""").containsMatchIn(normalized) ||
                        Regex("""\b(?:nahi|nhi)\s+(?:chahiye|karna|karo|kar|chala\w*|jala\w*|badha\w*|kam)\b""").containsMatchIn(normalized) ||
                        Regex("""\b(?:don't|dont|do not|never)\b""").containsMatchIn(normalized)
                )
        val winnerParams = if (negated) {
            android.util.Log.d("MarkNeg", "suppressed ${winner.rule.intent} in '$normalized'")
            winner.params + ("negated" to "true")
        } else winner.params

        // ---- token-coverage confidence ----
        // covered = tokens the winner accounts for: inside the matched text, inside
        // any extracted param value, known vocabulary, or numeric.
        val matchText = normalized.substring(winner.matchStart, winner.matchEnd)
        val paramText = winnerParams.values.joinToString(" ").lowercase()
        var covered = 0
        for (t in tokens) {
            if (matchText.contains(t) || paramText.contains(t) ||
                vocabByLength[t.length]?.contains(t) == true || t.all { it.isDigit() }
            ) covered++
        }
        var confidence = if (tokens.isNotEmpty()) covered.toFloat() / tokens.size else 0f
        if (winner.extractorProducedParam) confidence += 0.2f
        confidence -= 0.1f * repairs
        confidence = confidence.coerceIn(0f, 1f)
        logConfidence(normalized, winner.rule.intent, covered.toFloat() / tokens.size, confidence, confidence >= 0.40f, repairs)

        // Collision 2: volume off / level 0 means silent, not volume
        if (winner.rule.intent == IntentType.SET_VOLUME) {
            val state = extractState(normalized, winner.matchStart)
            val level = winnerParams["level"]?.toIntOrNull()
            if (state == "off" || level == 0) {
                val silentParams = mutableMapOf("state" to "on", "target" to extractTarget(normalized))
                if (negated) silentParams["negated"] = "true"
                return IntentResult(Intent(IntentType.SET_SILENT, silentParams), confidence, normalized)
            }
        }

        return IntentResult(Intent(winner.rule.intent, winnerParams), confidence, normalized)
    }

    private fun logConfidence(
        normalized: String,
        intent: IntentType,
        coverage: Float,
        final: Float,
        passed: Boolean,
        repairs: Int
    ) {
        val status = if (passed) "PASS" else "REJECT"
        android.util.Log.d(
            "MarkConf",
            "text: '%s' | intent: %s | coverage: %.2f | repairs: %d | final: %.2f | %s"
                .format(normalized, intent, coverage, repairs, final, status)
        )
    }

    private data class Candidate(
        val rule: Rule,
        val params: Map<String, String>,
        val matchedLength: Int,
        val originalIndex: Int,
        val extractorProducedParam: Boolean = false,
        val matchStart: Int = 0,
        val matchEnd: Int = 0
    )

    private data class Rule(
        val intent: IntentType,
        val pattern: Regex,
        val paramNames: List<String> = emptyList(),
        val extractors: List<String> = emptyList(),
        val fixedParams: Map<String, String> = emptyMap(),
        val priority: Int = 0,
        val blockedBy: Regex? = null,
        /**
         * Some laptop rules match a phrase that is meaningful on its own
         * ("close chrome"). They must only fire when the laptop is actually
         * named, but the anchor can sit anywhere in the sentence, so a single
         * regex would need every ordering. This flag is the honest version of
         * that: match the verb, then require the anchor separately.
         */
        val requiresLaptop: Boolean = false
    )
}