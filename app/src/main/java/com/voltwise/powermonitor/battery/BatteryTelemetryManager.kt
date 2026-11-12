package com.voltwise.powermonitor.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlin.math.abs

class BatteryTelemetryManager(private val context: Context) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /**
     * Obtains the instantaneous hardware battery snapshot via sticky intent and BatteryManager properties.
     */
    fun getBatterySnapshot(): BatteryInfo {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent: Intent? = context.registerReceiver(null, intentFilter)

        val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 50
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (scale > 0) ((rawLevel / scale.toFloat()) * 100).toInt() else rawLevel

        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3800) ?: 3800
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250
        val temperatureCelsius = rawTemp / 10.0f

        val statusInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
            BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
            else -> BatteryStatus.UNKNOWN
        }

        val pluggedInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugType = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            0 -> PlugType.BATTERY
            else -> PlugType.UNKNOWN
        }

        val healthInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.UNSPECIFIED_FAILURE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            else -> BatteryHealth.UNKNOWN
        }

        val technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        // Measure Current Draw in mA
        val currentNowMa = readNormalizedCurrent(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, status)
        val currentAvgMa = readNormalizedCurrent(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE, status)

        // Calculate instantaneous Power in mW = (V in volts) * (I in mA)
        val voltageVolts = voltageMv / 1000.0f
        val powerMw = voltageVolts * abs(currentNowMa)

        // Screen state
        val isScreenOn = powerManager?.isInteractive ?: true

        // Estimate remaining time
        val estimatedHours = estimateRemainingHours(level, currentAvgMa.takeIf { it != 0f } ?: currentNowMa, status)

        return BatteryInfo(
            level = level,
            scale = scale,
            voltageMv = voltageMv,
            temperatureCelsius = temperatureCelsius,
            currentNowMa = currentNowMa,
            currentAvgMa = currentAvgMa,
            powerMw = powerMw,
            status = status,
            plugType = plugType,
            health = health,
            technology = technology,
            estimatedRemainingHours = estimatedHours,
            isScreenOn = isScreenOn
        )
    }

    /**
     * Reads and normalizes hardware current property into signed milliamperes (mA).
     * Discharging is returned as negative mA, charging is returned as positive mA.
     */
    private fun readNormalizedCurrent(propertyId: Int, status: BatteryStatus): Float {
        val rawMicroAmps = batteryManager?.getIntProperty(propertyId) ?: 0
        if (rawMicroAmps == 0 || rawMicroAmps == Int.MIN_VALUE) {
            // Fallback: estimate typical idle or charging current if OEM driver reports 0
            return when (status) {
                BatteryStatus.CHARGING -> 1200f
                BatteryStatus.FULL -> 50f
                else -> -350f
            }
        }

        // Most modern Android devices report in microamperes (uA), e.g. -250000 uA = -250 mA.
        // Some older chipsets report directly in mA. If absolute value is > 10,000, treat as uA.
        var milliAmps = if (abs(rawMicroAmps) > 10_000) {
            rawMicroAmps / 1000.0f
        } else {
            rawMicroAmps.toFloat()
        }

        // Ensure sign aligns with charging state (some OEM kernels report positive value when discharging)
        if (status == BatteryStatus.CHARGING && milliAmps < 0) {
            milliAmps = abs(milliAmps)
        } else if (status == BatteryStatus.DISCHARGING && milliAmps > 0) {
            milliAmps = -milliAmps
        }

        return milliAmps
    }

    /**
     * Estimates hours remaining based on current discharge / charge rate and nominal device capacity.
     */
    private fun estimateRemainingHours(level: Int, currentMa: Float, status: BatteryStatus): Float? {
        val nominalCapacityMah = 4500f // Nominal smartphone capacity (4500 mAh)
        val rate = abs(currentMa)
        if (rate <= 10f) return null

        return when (status) {
            BatteryStatus.DISCHARGING -> {
                val remainingCapacity = (level / 100f) * nominalCapacityMah
                remainingCapacity / rate
            }
            BatteryStatus.CHARGING -> {
                val neededCapacity = ((100 - level) / 100f) * nominalCapacityMah
                neededCapacity / rate
            }
            else -> null
        }
    }
}
