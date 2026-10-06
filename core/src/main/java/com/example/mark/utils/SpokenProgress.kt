package com.example.mark.utils

/**
 * Which words of the current reply have actually been heard, built from the
 * TTS engine's callbacks, so on-screen text can follow the voice instead of
 * racing ahead of it on a timer.
 *
 * Engines that report word ranges (onRangeStart) move it word by word; others
 * move it a whole sentence at a time, as each sentence starts.
 *
 * Every method returns the new heard text, or null when nothing changed
 * (for example a callback from an utterance that was flushed by [reset]).
 */
class SpokenProgress {

    /** Queued utterances; null text for ones that are spoken but not shown (fillers). */
    private val texts = mutableMapOf<String, String?>()
    private val heard = StringBuilder()
    private var wordLevel = false

    /** A new reply (or stop): forget everything queued and heard. */
    @Synchronized
    fun reset(): String {
        texts.clear()
        heard.setLength(0)
        return ""
    }

    /** [shown] false for speech that is not part of the reply ("One moment, sir."). */
    @Synchronized
    fun queued(id: String, text: String, shown: Boolean = true) {
        texts[id] = if (shown) text else null
    }

    @Synchronized
    fun started(id: String?): String? {
        val text = texts[id] ?: return null
        // Until the engine has shown it reports words, show the sentence as it begins.
        return if (wordLevel) null else join(text)
    }

    /** [end] is the end of the word being spoken, within the utterance's text. */
    @Synchronized
    fun range(id: String?, end: Int): String? {
        val text = texts[id] ?: return null
        wordLevel = true
        return join(text.substring(0, end.coerceIn(0, text.length)))
    }

    /** [completed] is false when the utterance was stopped or failed. */
    @Synchronized
    fun finished(id: String?, completed: Boolean): String? {
        if (id == null || !texts.containsKey(id)) return null
        val text = texts.remove(id) ?: return null
        if (!completed) return null
        if (heard.isNotEmpty()) heard.append(' ')
        heard.append(text)
        return heard.toString()
    }

    private fun join(partial: String): String =
        if (heard.isEmpty()) partial else "$heard $partial"
}
