package com.example.mark.repository

import com.example.mark.model.MemoryFact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryFactsTest {

    private fun fact(text: String, at: Long) = MemoryFact(id = "id$at", text = text, createdAt = at)

    @Test
    fun normalizesWhitespaceAndTrailingDots() {
        assertEquals("Car keys are in the drawer", MemoryFacts.normalize("  Car  keys are in the drawer. "))
    }

    @Test
    fun duplicateIgnoresCaseAndPunctuation() {
        val existing = listOf(fact("Wife's birthday is 12 March", 1))
        assertTrue(MemoryFacts.isDuplicate(existing, "wife's birthday is 12 march."))
        assertFalse(MemoryFacts.isDuplicate(existing, "Mother's birthday is 12 March"))
    }

    @Test
    fun matchingNeedsEveryMeaningfulWord() {
        val facts = listOf(fact("His car keys are in the top drawer", 1), fact("His car is a red Swift", 2))
        assertEquals(listOf("His car keys are in the top drawer"), MemoryFacts.matching(facts, "car keys").map { it.text })
        assertEquals(2, MemoryFacts.matching(facts, "my car").size) // "my" is too short to count
        assertTrue(MemoryFacts.matching(facts, "of").isEmpty())
        assertTrue(MemoryFacts.matching(facts, "bike").isEmpty())
    }

    @Test
    fun addingBeyondCapDropsOldest() {
        val full = (1..MemoryFacts.MAX_FACTS).map { fact("f$it", it.toLong()) }
        val after = MemoryFacts.withAdded(full, fact("newest", 10_000))
        assertEquals(MemoryFacts.MAX_FACTS, after.size)
        assertFalse(after.any { it.text == "f1" })
        assertEquals("newest", after.last().text)
    }

    @Test
    fun promptGetsNewestFactsOldestFirst() {
        val facts = (1..60).map { fact("f$it", it.toLong()) }.shuffled()
        val prompt = MemoryFacts.forPrompt(facts)
        assertEquals(MemoryFacts.PROMPT_FACTS, prompt.size)
        assertEquals("f21", prompt.first())
        assertEquals("f60", prompt.last())
    }
}
