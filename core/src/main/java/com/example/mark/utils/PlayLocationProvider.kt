package com.example.mark.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.mark.assistant.LocationProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

/**
 * Uses Google Play Services fused location. Same implementation works on Wear OS,
 * so the wear module can reuse it if the class is placed in core later.
 */
class PlayLocationProvider(private val context: Context) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    override suspend fun currentCoords(): Pair<Double, Double>? {
        if (!hasPermission()) return null

        // getCurrentLocation forces a fresh fix; lastLocation can be stale or null.
        val location = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        }.getOrNull() ?: return null

        return location.latitude to location.longitude
    }

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun lastKnownCoords(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        val location = runCatching {
            client.lastLocation.await()
        }.getOrNull() ?: return null
        return location.latitude to location.longitude
    }
}
