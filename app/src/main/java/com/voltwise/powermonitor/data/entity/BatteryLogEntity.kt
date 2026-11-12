package com.voltwise.powermonitor.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a discrete point-in-time snapshot of hardware battery and power telemetry.
 */
@Entity(tableName = "battery_logs")
data class BatteryLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val level: Int,                   // Battery level in percentage (0 - 100)
    val voltageMv: Int,               // Voltage in millivolts (e.g., 3850 mV)
    val temperatureCelsius: Float,    // Battery temperature in Celsius (e.g., 31.5 °C)
    val currentMa: Float,             // Instantaneous current draw in mA (signed: <0 discharging, >0 charging)
    val powerMw: Float,               // Instantaneous power draw in mW (|V * I|)
    val status: String,               // Charging, Discharging, Full, Not Charging
    val plugType: String,             // Battery (unplugged), AC, USB, Wireless
    val screenOn: Boolean             // Whether device screen was active at sample time
)
