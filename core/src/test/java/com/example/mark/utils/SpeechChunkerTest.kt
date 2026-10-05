package com.example.mark.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechChunkerTest {

    @Test
    fun noBoundaryYet() {
        assertEquals(-1, SpeechChunker.cutPoint("It is twenty three", 1))
    }

    @Test
    fun cutsAfterSentenceFollowedBySpace() {
        val text = "Done, sir. Anything else"
        assertEquals("Done, sir.".length, SpeechChunker.cutPoint(text, 1))
    }

    @Test
    fun doesNotSplitDecimals() {
        assertEquals(-1, SpeechChunker.cutPoint("It is 23.5 degrees", 1))
    }

    @Test
    fun trailingPunctuationWaitsForMoreText() {
        // "23." may still become "23.5" when the next chunk arrives.
        assertEquals(-1, SpeechChunker.cutPoint("It is 23.", 1))
    }

    @Test
    fun prefersLongestPrefix() {
        val text = "One. Two! Three? Four"
        assertEquals("One. Two! Three?".length, SpeechChunker.cutPoint(text, 1))
    }

    @Test
    fun respectsMinimumLength() {
        assertEquals(-1, SpeechChunker.cutPoint("Hi. there", 12))
        assertEquals("Hello there, sir.".length, SpeechChunker.cutPoint("Hello there, sir. More", 12))
    }

    @Test
    fun handlesNewlineAndDanda() {
        assertEquals("Line one\n".length, SpeechChunker.cutPoint("Line one\nline two", 1))
        assertEquals("Theek hai।".length, SpeechChunker.cutPoint("Theek hai। aur", 1))
    }
}
