package com.example.mark.assistant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedSituationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun firstCallReturnsFreshSnapshot() = runBlocking {
        val cache = CachedSituation({ listOf("Phone battery: 50%.") }, scope)
        assertEquals(listOf("Phone battery: 50%."), cache.snapshot())
    }

    @Test
    fun slowSourceNeverHoldsUpTheTurn() = runBlocking {
        val cache = CachedSituation({ awaitCancellation() }, scope, firstWaitMs = 50)
        val started = System.nanoTime()
        assertEquals(emptyList<String>(), cache.snapshot())
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000)
    }

    @Test
    fun servesCacheThenRefreshesInBackgroundWhenStale() = runBlocking {
        var now = 0L
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val cache = CachedSituation(
            { calls++; if (calls > 1) gate.await(); listOf("v$calls") },
            scope, maxAgeMs = 1_000, clock = { now }
        )
        assertEquals(listOf("v1"), cache.snapshot())
        now = 500
        assertEquals("fresh enough: no refresh", listOf("v1"), cache.snapshot())
        assertEquals(1, calls)

        now = 2_000
        assertEquals("stale: old value served instantly", listOf("v1"), cache.snapshot())
        gate.complete(Unit)
        withTimeout(2_000) { while (cache.snapshot() != listOf("v2")) delay(5) }
        assertEquals(2, calls)
    }

    @Test
    fun failingSourceYieldsNothing() = runBlocking {
        val cache = CachedSituation({ error("calendar exploded") }, scope)
        assertEquals(emptyList<String>(), cache.snapshot())
    }
}
