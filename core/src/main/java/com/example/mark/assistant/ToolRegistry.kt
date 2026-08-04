package com.example.mark.assistant

import com.example.mark.router.IntentType

/**
 * Holds the tools available on this device. The phone and the watch register
 * different sets — the watch has a heart-rate sensor, the phone has
 * Health Connect — and nothing above this layer needs to know that.
 */
class ToolRegistry(tools: List<Tool>) {

    private val byName: Map<String, Tool> = tools.associateBy { it.name }

    private val byIntent: Map<IntentType, Tool> = tools
        .mapNotNull { tool -> tool.intent?.let { it to tool } }
        .toMap()

    init {
        require(byName.size == tools.size) {
            "Duplicate tool name: ${tools.groupBy { it.name }.filterValues { it.size > 1 }.keys}"
        }
    }

    val all: List<Tool> = tools

    operator fun get(name: String): Tool? = byName[name]

    operator fun get(intent: IntentType): Tool? = byIntent[intent]

    fun supports(intent: IntentType): Boolean = intent in byIntent
}
