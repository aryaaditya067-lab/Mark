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
    suspend fun todaySteps(): Int? = withTimeoutOrNull(2000) {
        suspendCancellableCoroutine { cont ->
            val callback = object : MeasureCallback {
                override fun onAvailabilityChanged(dataType: androidx.health.services.client.data.DeltaDataType<*, *>, availability: androidx.health.services.client.data.Availability) {}
                override fun onDataReceived(data: DataPointContainer) {
                    val steps = data.getData(DataType.STEPS).lastOrNull()?.value
                    if (cont.isActive) cont.resume(steps?.toInt())
                }
            }
            measureClient.registerMeasureCallback(DataType.STEPS, callback)
            cont.invokeOnCancellation { measureClient.unregisterMeasureCallbackAsync(DataType.STEPS, callback) }
        }
    }

    suspend fun lastNightSleep(): Int? = null // Fallback to Firestore
}
