package com.example.mark.repository

import com.example.mark.model.MemoryFact

/** The rules for a memory book, kept free of storage so they can be tested. */
object MemoryFacts {

    /** Facts beyond this are dropped oldest-first; every one is sent with each LLM request. */
    const val MAX_FACTS = 100

    /** How many facts go into one prompt. */
    const val PROMPT_FACTS = 40

    private const val MAX_LENGTH = 200

    val KINDS = setOf("person", "preference", "fact", "place", "routine")

    fun normalize(text: String): String =
        text.trim().replace(Regex("\\s+"), " ").trimEnd('.', ' ').take(MAX_LENGTH)

    fun isDuplicate(existing: List<MemoryFact>, text: String): Boolean {
        val wanted = normalize(text).lowercase()
        return existing.any { normalize(it.text).lowercase() == wanted }
    }

    /**
     * Facts that mention every meaningful word of [query]. Requiring all words
     * keeps "forget my car keys" from wiping every fact that mentions "my".
     */
    fun matching(facts: List<MemoryFact>, query: String): List<MemoryFact> {
        val words = words(query).filter { it.length > 2 }
        if (words.isEmpty()) return emptyList()
        // Whole words: "car" must not match "Oscar" or "card".
        return facts.filter { fact -> val factWords = words(fact.text).toSet(); words.all { it in factWords } }
    }

    private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /** Adds [fact], keeping at most [MAX_FACTS] by dropping the oldest. */
    fun withAdded(existing: List<MemoryFact>, fact: MemoryFact): List<MemoryFact> =
        (existing + fact).sortedBy { it.createdAt }.takeLast(MAX_FACTS)

    /** The newest [PROMPT_FACTS] facts, oldest first so they read like a history. */
    fun forPrompt(facts: List<MemoryFact>): List<String> =
        facts.sortedBy { it.createdAt }.takeLast(PROMPT_FACTS).map { it.text }
}
