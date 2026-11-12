package com.voltwise.powermonitor.usage

import android.graphics.drawable.Drawable

/**
 * Model representing application usage metrics, activity state, and hardware resource footprint.
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val foregroundTimeMs: Long,
    val lastTimeUsed: Long,
    val standbyBucket: String,
    val isSystemApp: Boolean,
    val estimatedPowerMah: Float = 0f,
    val percentageOfTotal: Float = 0f,
    val isCurrentlyActive: Boolean = false
) {
    /**
     * Formatted foreground execution duration string (e.g., "1h 24m" or "45s").
     */
    val formattedDuration: String
        get() {
            val totalSeconds = foregroundTimeMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }
}
