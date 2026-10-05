package com.example.mark.tools

import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.model.MemoryFact
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.repository.MemoryFacts
import com.example.mark.repository.MemoryStore

/**
 * Long-term memory. LLM-only (no offline intent): turning "yaad rakhna, meri
 * car ki chabi drawer mein hai" into a clean fact needs language understanding.
 */
class RememberFactTool(private val store: MemoryStore) : Tool {

    override val name = "remember_fact"

    override val definition = FunctionDef(
        name = name,
        description = "Save a lasting fact about the user so you can use it in future conversations: " +
            "people in their life, preferences, where they keep things, routines. Use it when they say " +
            "'remember', 'yaad rakhna', or tell you something clearly worth keeping. Not for to-dos " +
            "(use add_task) and never for passwords, PINs or bank details.",
        parameters = Parameters(
            properties = mapOf(
                "fact" to Property("string", "The fact as a short English sentence about the user, e.g. 'His car keys are in the top drawer.'"),
                "kind" to Property("string", "One of: person, preference, fact, place, routine")
            ),
            required = listOf("fact")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val text = MemoryFacts.normalize(request.string("fact").orEmpty())
        if (text.isBlank()) return ToolResult.Failure("No fact was given.", reason = "missing_arg")
        val existing = store.all()
        if (MemoryFacts.isDuplicate(existing, text)) return ToolResult.Success("Already remembered: $text")
        val kind = request.string("kind")?.lowercase()?.takeIf { it in MemoryFacts.KINDS } ?: "fact"
        store.add(MemoryFact(text = text, kind = kind))
        return ToolResult.Success("Remembered: $text")
    }
}

class ForgetFactTool(private val store: MemoryStore) : Tool {

    override val name = "forget_fact"

    override val definition = FunctionDef(
        name = name,
        description = "Forget facts you remembered about the user, when they ask you to forget or say a " +
            "fact is wrong. Removes every remembered fact containing all the words of the query.",
        parameters = Parameters(
            properties = mapOf(
                "query" to Property("string", "Key words of the fact to forget, e.g. 'car keys'")
            ),
            required = listOf("query")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val query = request.string("query") ?: return ToolResult.Failure("Nothing to forget was named.", reason = "missing_arg")
        val matches = MemoryFacts.matching(store.all(), query)
        if (matches.isEmpty()) return ToolResult.Failure("No remembered fact matches '$query'.", reason = "not_found")
        store.remove(matches.map { it.id }.toSet())
        return ToolResult.Success("Forgot: " + matches.joinToString("; ") { it.text })
    }
}
