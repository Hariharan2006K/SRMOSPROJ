package com.voltwise.powermonitor.estimator

import com.voltwise.powermonitor.data.entity.BatteryLogEntity
import com.voltwise.powermonitor.usage.AppUsageInfo
import kotlin.math.abs
import kotlin.math.max

/**
 * Scientific Operating System Battery Attribution Engine.
 * Combines hardware battery telemetry (voltage, discharge current, capacity drop)
 * with Android process & activity execution metrics (UsageStatsManager foreground time,
 * standby buckets) to estimate energy consumption per task.
 */
object PowerAttributionEngine {

    const val DEFAULT_BATTERY_CAPACITY_MAH = 4500f // Typical modern Android battery capacity

    /**
     * Calculates the estimated energy consumed (in mAh and percentage) for each application
     * over the sample observation window.
     */
    fun attributePower(
        apps: List<AppUsageInfo>,
        batteryLogs: List<BatteryLogEntity>,
        timeWindowMs: Long
    ): List<AppUsageInfo> {
        if (apps.isEmpty()) return emptyList()

        // 1. Calculate total energy drained during the period (in mAh)
        val totalDrainedMah = calculateTotalDischargeMah(batteryLogs, timeWindowMs)

        // 2. Compute activity weight per application based on foreground duration & standby bucket
        val weightedScores = apps.map { app ->
            val bucketMultiplier = when (app.standbyBucket) {
                "ACTIVE" -> 1.0f
                "WORKING_SET" -> 0.85f
                "FREQUENT" -> 0.70f
                "RARE" -> 0.50f
                "RESTRICTED" -> 0.30f
                else -> 0.80f
            }
            // Weight = Foreground time in seconds * Standby coefficient
            val durationSec = max(1L, app.foregroundTimeMs / 1000)
            val score = durationSec * bucketMultiplier
            Pair(app, score)
        }

        val totalScore = weightedScores.sumOf { it.second.toDouble() }.toFloat()

        if (totalScore <= 0f) {
            return apps
        }

        // 3. System & screen baseline power fraction (typically ~15% baseline for display/OS kernel)
        val baselineOsFraction = 0.15f
        val allocatableMah = totalDrainedMah * (1.0f - baselineOsFraction)

        // 4. Distribute energy across applications
        return weightedScores.map { (app, score) ->
            val fraction = score / totalScore
            val appMah = (allocatableMah * fraction).coerceAtLeast(0.1f)
            val percent = if (totalDrainedMah > 0) (appMah / totalDrainedMah) * 100f else 0f

            app.copy(
                estimatedPowerMah = (appMah * 10).toInt() / 10f, // Rounded to 1 decimal place
                percentageOfTotal = (percent * 10).toInt() / 10f
            )
        }.sortedByDescending { it.estimatedPowerMah }
    }

    /**
     * Determines total mAh discharged by examining telemetry logs (level drop or average current integration).
     */
    private fun calculateTotalDischargeMah(
        logs: List<BatteryLogEntity>,
        timeWindowMs: Long
    ): Float {
        if (logs.size >= 2) {
            val first = logs.first()
            val last = logs.last()

            // Method A: Level drop
            val levelDrop = first.level - last.level
            if (levelDrop > 0) {
                val levelDropMah = (levelDrop / 100f) * DEFAULT_BATTERY_CAPACITY_MAH
                return max(levelDropMah, 5f)
            }

            // Method B: Discharge Current integration I_avg * time
            val dischargingLogs = logs.filter { it.currentMa < 0 }
            if (dischargingLogs.isNotEmpty()) {
                val avgCurrentMa = dischargingLogs.map { abs(it.currentMa) }.average().toFloat()
                val hours = timeWindowMs / (1000f * 3600f)
                val integratedMah = avgCurrentMa * hours
                if (integratedMah > 1f) {
                    return integratedMah
                }
            }
        }

        // Fallback: Estimate baseline discharge based on observation duration (typical ~350 mA avg smartphone drain)
        val hours = timeWindowMs / (1000f * 3600f)
        return max(5f, 350f * hours)
    }

    /**
     * Detects rogue or abnormally power-hungry applications.
     */
    fun detectPowerHogs(apps: List<AppUsageInfo>): List<PowerHogInsight> {
        val insights = mutableListOf<PowerHogInsight>()

        for (app in apps) {
            // Anomaly 1: Consumes more than 20% of total battery
            if (app.percentageOfTotal >= 20f && app.estimatedPowerMah > 100f) {
                insights.add(
                    PowerHogInsight(
                        appName = app.appName,
                        packageName = app.packageName,
                        severity = HogSeverity.HIGH,
                        reason = "Heavy Battery Consumer: Accounting for ${app.percentageOfTotal}% of total discharge (${app.estimatedPowerMah} mAh)."
                    )
                )
            }
            // Anomaly 2: Restricted or rare app running significant background time
            else if ((app.standbyBucket == "RARE" || app.standbyBucket == "RESTRICTED") && app.foregroundTimeMs > 10 * 60 * 1000) {
                insights.add(
                    PowerHogInsight(
                        appName = app.appName,
                        packageName = app.packageName,
                        severity = HogSeverity.MEDIUM,
                        reason = "Standby Bucket Mismatch: App is categorized as ${app.standbyBucket} but accumulated significant active runtime."
                    )
                )
            }
        }

        return insights
    }
}

data class PowerHogInsight(
    val appName: String,
    val packageName: String,
    val severity: HogSeverity,
    val reason: String
)

enum class HogSeverity {
    LOW,
    MEDIUM,
    HIGH
}
