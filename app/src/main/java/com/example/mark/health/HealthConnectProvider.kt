package com.example.mark.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.mark.assistant.HealthProvider
import com.example.mark.model.HealthSnapshot
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reads from Health Connect, the platform store that Samsung Health and other
 * trackers write into. This means Mark reports the same numbers the user sees
 * in their own health app, whether or not Mark was running.
 */
class HealthConnectProvider(private val context: Context) : HealthProvider {

    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    /** Null when Health Connect is not installed or not supported. */
    private fun clientOrNull(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }

    /** For the permission launcher in the UI. */
    fun requiredPermissions(): Set<String> = permissions

    fun permissionContract() =
        PermissionController.createRequestPermissionResultContract()

    override suspend fun isAvailable(): Boolean {
        val client = clientOrNull() ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    override suspend fun todaySteps(): Int? {
        val client = clientOrNull() ?: return null

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant()
        val now = java.time.Instant.now()

        // Samsung Health writes steps at several granularities at once, often
        // creating overlapping records where one interval fully contains another.
        // A plain sum double-counts these. aggregate() is often too aggressive
        // and under-counts. Reading the raw records and manually filtering
        // out contained intervals provides the most accurate total.
        val records = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            ).records
        }.getOrNull() ?: return null

        if (records.isEmpty()) return null

        val sorted = records.sortedBy { it.startTime }
        val kept = sorted.filter { candidate ->
            sorted.none { other ->
                other !== candidate &&
                        !other.startTime.isAfter(candidate.startTime) &&
                        !other.endTime.isBefore(candidate.endTime) &&
                        Duration.between(other.startTime, other.endTime) >
                        Duration.between(candidate.startTime, candidate.endTime)
            }
        }

        return kept.sumOf { it.count }.toInt()
    }

    override suspend fun lastNightSleep(): HealthSnapshot? {
        val client = clientOrNull() ?: return null

        val zone = ZoneId.systemDefault()
        // Night runs from yesterday 18:00 to today 12:00. A nap at 5 PM is not sleep.
        val from = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(18, 0))
            .atZone(zone).toInstant()
        
        val noon = LocalDateTime.of(LocalDate.now(), LocalTime.NOON).atZone(zone).toInstant()
        val now = java.time.Instant.now()
        val to = if (noon.isBefore(now)) noon else now

        val sessions = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to)
                )
            ).records
        }.getOrNull().orEmpty()

        if (sessions.isEmpty()) return null

        // Sessions can be fragmented. Treat them all as one night.
        val totalMinutes = sessions.sumOf {
            Duration.between(it.startTime, it.endTime).toMinutes()
        }.toInt()

        return HealthSnapshot(
            sleepMinutes = totalMinutes,
            sleepStart = sessions.minOf { it.startTime }.toEpochMilli(),
            sleepEnd = sessions.maxOf { it.endTime }.toEpochMilli()
        )
    }
}
