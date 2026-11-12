package com.voltwise.powermonitor.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks a continuous discharge or monitoring session from start to finish.
 */
@Entity(tableName = "monitoring_sessions")
data class MonitoringSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int,
    val levelDrop: Int,
    val avgCurrentMa: Float,
    val peakTemperature: Float,
    val activeAppCount: Int
)
