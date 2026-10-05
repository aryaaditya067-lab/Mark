package com.example.mark.utils

/**
 * Decides where streamed text can be cut and handed to TTS.
 *
 * Only a real sentence end counts: ". ", "! ", "? ", the Hindi danda "।" or
 * a newline. A bare "." is not enough — "23.5 degrees" must not be spoken as
 * "23." and then "5 degrees".
 */
object SpeechChunker {

    private val SENTENCE_END = Regex("""[.!?।](?=\s)|\n""")

    /**
     * Returns the length of the prefix of [buffer] that ends on a sentence
     * boundary and is at least [minLength] characters long, or -1 if there is
     * none yet. Prefers the longest such prefix, so fewer, smoother utterances.
     */
    fun cutPoint(buffer: String, minLength: Int): Int {
        val last = SENTENCE_END.findAll(buffer).lastOrNull() ?: return -1
        val end = last.range.last + 1
        return if (end >= minLength) end else -1
    }
}
