package com.example.mark.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A few short lines about the user's situation right now ("Phone battery 18%",
 * "Next event: Standup today at 10:00"), added to every LLM request so Mark can
 * use them when relevant without a tool call.
 */
fun interface SituationProvider {
    suspend fun snapshot(): List<String>
}

/**
 * Serves the last snapshot instantly and refreshes it in the background once
 * it is older than [maxAgeMs]. Reading the calendar or Firestore must never
 * hold up a reply: the very first call waits at most [firstWaitMs].
 */
class CachedSituation(
    private val source: SituationProvider,
    private val scope: CoroutineScope,
    private val maxAgeMs: Long = 5 * 60_000L,
    private val firstWaitMs: Long = 300L,
    private val clock: () -> Long = System::currentTimeMillis,
) : SituationProvider {

    @Volatile private var last: List<String>? = null
    @Volatile private var takenAt = 0L
    private var inFlight: Deferred<List<String>>? = null

    override suspend fun snapshot(): List<String> {
        val cached = last
        if (cached != null) {
            if (clock() - takenAt > maxAgeMs) refresh()
            return cached
        }
        val first = refresh()
        return withTimeoutOrNull(firstWaitMs) { first.await() } ?: emptyList()
    }

    /** Drops the cached snapshot, e.g. after the user changes something it shows. */
    fun invalidate() { takenAt = 0L }

    private fun refresh(): Deferred<List<String>> = synchronized(this) {
        inFlight?.takeIf { it.isActive } ?: scope.async {
            val lines = runCatching { source.snapshot() }.getOrDefault(emptyList())
            last = lines
            takenAt = clock()
            lines
        }.also { inFlight = it }
    }
}
