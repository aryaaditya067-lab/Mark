package com.example.mark.tools

/** Pure helpers for the laptop tool, kept free of Android so they can be tested. */
object LaptopCommands {

    /** Actions that end the user's session on the laptop; they need a spoken yes. */
    val DESTRUCTIVE = setOf("shutdown", "restart", "logoff")

    /** Actions whose meaning lives in free text (a search, something to type). */
    val NEEDS_PAYLOAD = setOf("browser", "type", "clipboard_set")

    // Command words around the payload, in English and Hinglish.
    private val COMMAND_WORDS = setOf(
        "hey", "mark", "please", "laptop", "lapy", "pc", "computer", "macbook", "desktop", "my", "the", "on",
        "pe", "par", "mein", "me", "se", "ko", "ka", "ki", "for", "about", "to", "in",
        "google", "search", "dhoondh", "dhundh", "look", "up", "find", "online", "youtube", "yt",
        "type", "likh", "likho", "write", "this", "copy", "clipboard", "daal", "do", "de", "kar", "karo", "karna"
    )

    /**
     * The free text of a command: "laptop pe google karo weather in delhi" ->
     * "weather in delhi". The offline router matches the verb but captures no
     * payload, so without this the laptop received an empty search.
     */
    fun payloadFrom(rawInput: String?): String? {
        val words = rawInput.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val first = words.indexOfFirst { it.lowercase().trim(',', '.', '?', '!') !in COMMAND_WORDS }
        if (first < 0) return null
        val last = words.indexOfLast { it.lowercase().trim(',', '.', '?', '!') !in COMMAND_WORDS }
        return words.subList(first, last + 1).joinToString(" ").trim(',', '.', '?', '!', ' ').ifBlank { null }
    }
}
