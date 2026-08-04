package com.example.mark.router

class IntentRouter {

    private val regexResolver = RegexIntentResolver()

    fun route(input: String): RoutingDecision {
        val result = regexResolver.resolve(input)

        // Threshold raised 0.20 -> 0.40. The old matchedChars/textLen confidence
        // punished long descriptive commands, forcing the gate this low; the new
        // token-coverage confidence scores real commands 0.6-1.0 and rambling
        // sentences that merely brush a keyword well below 0.40.
        return if (result.intent != null && result.confidence >= 0.40f) {
            android.util.Log.d("MarkMiss", "matched: ${result.intent} conf=%.2f <- ${result.normalizedText}".format(result.confidence))
            RoutingDecision.Offline(result.intent)
        } else {
            android.util.Log.d("MarkMiss", "unmatched (conf=%.2f): ${result.normalizedText}".format(result.confidence))
            RoutingDecision.Online
        }
    }
}