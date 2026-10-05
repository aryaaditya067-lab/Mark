package com.example.mark.health

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Reads health data from the watch's local sensors using Health Services.
 */
class LocalHealthReader(context: Context) {

    private val measureClient = HealthServices.getClient(context).measureClient

    /**
     * Reads current step delta. Note: Health Services MeasureClient only 
     * provides deltas; today's total usually requires PassiveMonitoringClient.
     */
    suspend fun todaySteps(): Int? {
        var registered: MeasureCallback? = null
        return try {
            withTimeoutOrNull(2000) {
                suspendCancellableCoroutine { cont ->
                    val callback = object : MeasureCallback {
                        override fun onAvailabilityChanged(dataType: androidx.health.services.client.data.DeltaDataType<*, *>, availability: androidx.health.services.client.data.Availability) {}
                        override fun onDataReceived(data: DataPointContainer) {
                            val steps = data.getData(DataType.STEPS).lastOrNull()?.value
                            if (cont.isActive) cont.resume(steps?.toInt())
                        }
                    }
                    registered = callback
                    measureClient.registerMeasureCallback(DataType.STEPS, callback)
                }
            }
        } finally {
            // Always release the sensor, not only on cancellation.
            registered?.let { measureClient.unregisterMeasureCallbackAsync(DataType.STEPS, it) }
        }
    }

    suspend fun lastNightSleep(): Int? = null // Fallback to Firestore
}
