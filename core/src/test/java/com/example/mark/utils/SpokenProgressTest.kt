package com.example.mark.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenProgressTest {

    @Test
    fun followsWordRangesAcrossSentences() {
        val p = SpokenProgress()
        p.queued("a", "It is sunny.")
        p.queued("b", "Take sunglasses.")
        assertEquals("It is sunny.", p.started("a")) // no word ranges seen yet
        assertEquals("It", p.range("a", 2))
        p.finished("a", completed = true)
        assertNull(p.started("b")) // word level now: wait for the first word
        assertEquals("It is sunny. Take", p.range("b", 4))
        assertEquals("It is sunny. Take sunglasses.", p.finished("b", completed = true))
    }

    @Test
    fun withoutWordRangesShowsEachSentenceAsItStarts() {
        val p = SpokenProgress()
        p.queued("a", "First.")
        p.queued("b", "Second.")
        assertEquals("First.", p.started("a"))
        assertEquals("First.", p.finished("a", completed = true))
        assertEquals("First. Second.", p.started("b"))
    }

    @Test
    fun fillersAreSpokenButNeverShown() {
        val p = SpokenProgress()
        p.queued("f", "One moment, sir.", shown = false)
        p.queued("a", "Done.")
        assertNull(p.started("f"))
        assertNull(p.range("f", 3))
        assertNull(p.finished("f", completed = true))
        assertEquals("Done.", p.started("a"))
    }

    @Test
    fun callbacksFromFlushedUtterancesAreIgnored() {
        val p = SpokenProgress()
        p.queued("old", "Old reply.")
        p.reset()
        p.queued("new", "New reply.")
        assertNull(p.range("old", 3))
        assertNull(p.finished("old", completed = true))
        assertEquals("New", p.range("new", 3))
    }

    @Test
    fun stoppedUtteranceIsNotCountedAsHeard() {
        val p = SpokenProgress()
        p.queued("a", "Cut off here.")
        p.range("a", 3)
        assertNull(p.finished("a", completed = false))
        p.queued("b", "Next.")
        assertEquals("Next.", p.range("b", 5))
    }
}
