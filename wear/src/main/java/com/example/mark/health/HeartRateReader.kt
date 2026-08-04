package com.example.mark.health

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.guava.await
import kotlin.coroutines.resume

/**
 * Reads a single heart-rate sample from the watch's optical sensor.
 *
 * The sensor is not always on. It needs several seconds of skin contact before
 * it produces a usable value, so this suspends until the first sample arrives
 * or the timeout expires.
 */
class HeartRateReader(context: Context) {

    private val measureClient = HealthServices.getClient(context).measureClient

    suspend fun isSupported(): Boolean = runCatching {
        val caps = measureClient.getCapabilitiesAsync().await()
        DataType.HEART_RATE_BPM in caps.supportedDataTypesMeasure
    }.getOrDefault(false)

    /** @return bpm, or null if the sensor never produced a reading in time. */
    suspend fun readOnce(timeoutMs: Long = 20_000): Double? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val callback = object : MeasureCallback {

                    override fun onAvailabilityChanged(
                        dataType: DeltaDataType<*, *>,
                        availability: Availability
                    ) {
                        // Ignored — we just wait for data, or time out.
                    }

                    override fun onDataReceived(data: DataPointContainer) {
                        val bpm = data.getData(DataType.HEART_RATE_BPM)
                            .lastOrNull()
                            ?.value
                            ?: return

                        if (cont.isActive) cont.resume(bpm)
                    }
                }

                measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)

                cont.invokeOnCancellation {
                    measureClient.unregisterMeasureCallbackAsync(
                        DataType.HEART_RATE_BPM, callback
                    )
                }
            }
        }
}

