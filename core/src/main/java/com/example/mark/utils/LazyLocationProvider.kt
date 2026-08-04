package com.example.mark.utils

import com.example.mark.assistant.LocationProvider

/**
 * Defers construction of a location provider until first use.
 * Critical on Wear OS to avoid touching Play Services Location classes during
 * app startup if not needed.
 */
class LazyLocationProvider(private val factory: () -> LocationProvider) : LocationProvider {
    private val delegate by lazy { factory() }

    override suspend fun currentCoords(): Pair<Double, Double>? = delegate.currentCoords()

    override fun hasPermission(): Boolean = delegate.hasPermission()
}
