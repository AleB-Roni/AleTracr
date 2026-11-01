package com.aleb.aletracr.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

data class Activity(val steps: Long, val activeKcal: Double)

/**
 * Reads steps and active calories from Health Connect (Google Fit, Samsung Health,
 * Garmin, … write into it). Active calories are added to the daily budget.
 */
object HealthConnectManager {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun hasPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        return granted.containsAll(PERMISSIONS)
    }

    /** Today's activity, or null if Health Connect is unavailable / not permitted. */
    suspend fun today(context: Context, date: LocalDate = LocalDate.now()): Activity? {
        if (!hasPermissions(context)) return null
        return try {
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val result = HealthConnectClient.getOrCreate(context).aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            )
            Activity(
                steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
                activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0,
            )
        } catch (_: Exception) {
            null
        }
    }
}
