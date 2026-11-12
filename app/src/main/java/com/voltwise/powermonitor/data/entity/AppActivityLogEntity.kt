package com.voltwise.powermonitor.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents energy consumption and foreground/background activity metrics attributed to an application.
 */
@Entity(tableName = "app_activity_logs")
data class AppActivityLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,              // Sample period reference timestamp
    val packageName: String,
    val appName: String,
    val foregroundDurationMs: Long,   // Total foreground duration in milliseconds during evaluation window
    val lastTimeUsed: Long,           // Epoch timestamp of last active user interaction
    val estimatedPowerMah: Float,     // Estimated energy consumed in milliamp-hours (mAh)
    val percentageOfTotal: Float,     // Fraction of total device battery discharge consumed by this app
    val standbyBucket: String,        // ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED
    val isSystemApp: Boolean
)
