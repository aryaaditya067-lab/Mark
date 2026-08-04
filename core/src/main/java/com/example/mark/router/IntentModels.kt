package com.example.mark.router

data class Intent(
    val type: IntentType,
    val params: Map<String, String> = emptyMap()
)

data class IntentResult(
    val intent: Intent?,
    val confidence: Float,
    val normalizedText: String = ""
) {
    companion object {
        val unresolved = IntentResult(null, 0f, "")
    }
}

sealed interface RoutingDecision {
    data class Offline(val intent: Intent) : RoutingDecision
    data object Online : RoutingDecision
}
