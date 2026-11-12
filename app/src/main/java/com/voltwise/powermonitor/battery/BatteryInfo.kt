package com.voltwise.powermonitor.battery

/**
 * Encapsulates complete real-time battery hardware state.
 */
data class BatteryInfo(
    val level: Int,                   // Battery level (0 - 100%)
    val scale: Int,                   // Maximum level scale (usually 100)
    val voltageMv: Int,               // Millivolts (e.g., 3850 mV)
    val temperatureCelsius: Float,    // Celsius (e.g., 31.5 °C)
    val currentNowMa: Float,          // Instantaneous Current in mA (signed: <0 discharging, >0 charging)
    val currentAvgMa: Float,          // Average Current in mA
    val powerMw: Float,               // Instantaneous Power in milliwatts
    val status: BatteryStatus,        // Charging, Discharging, Full, etc.
    val plugType: PlugType,           // Battery, AC, USB, Wireless
    val health: BatteryHealth,        // Good, Overheat, etc.
    val technology: String,           // Li-ion, Li-poly, etc.
    val estimatedRemainingHours: Float?, // Estimated hours remaining until 0% (if discharging) or 100% (if charging)
    val isScreenOn: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

enum class BatteryStatus {
    CHARGING,
    DISCHARGING,
    FULL,
    NOT_CHARGING,
    UNKNOWN
}

enum class PlugType {
    BATTERY,
    AC,
    USB,
    WIRELESS,
    UNKNOWN
}

enum class BatteryHealth {
    GOOD,
    OVERHEAT,
    DEAD,
    OVER_VOLTAGE,
    UNSPECIFIED_FAILURE,
    COLD,
    UNKNOWN
}
