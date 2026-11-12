package com.voltwise.powermonitor.usage

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.voltwise.powermonitor.R

class AppUsageManager(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager = context.packageManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    /**
     * Verifies whether the end-user has granted PACKAGE_USAGE_STATS in Android Settings.
     */
    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Direct Intent to open Android's Usage Access authorization page.
     */
    fun getUsageAccessIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Direct Intent to open Battery Optimization exemption page.
     */
    fun getBatteryOptimizationIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Retrieves aggregated app usage metrics across the specified time window.
     */
    fun queryAppUsage(startTime: Long, endTime: Long): List<AppUsageInfo> {
        if (!hasUsagePermission() || usageStatsManager == null) {
            return emptyList()
        }

        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        ) ?: emptyList()

        if (usageStatsList.isEmpty()) {
            return emptyList()
        }

        // Aggregate by package name (since INTERVAL_BEST can return multiple slices)
        val aggregatedMap = mutableMapOf<String, AggregatedStat>()
        for (stat in usageStatsList) {
            if (stat.totalTimeInForeground <= 0) continue
            val existing = aggregatedMap[stat.packageName]
            if (existing != null) {
                existing.totalTimeInForeground += stat.totalTimeInForeground
                if (stat.lastTimeUsed > existing.lastTimeUsed) {
                    existing.lastTimeUsed = stat.lastTimeUsed
                }
            } else {
                aggregatedMap[stat.packageName] = AggregatedStat(
                    packageName = stat.packageName,
                    totalTimeInForeground = stat.totalTimeInForeground,
                    lastTimeUsed = stat.lastTimeUsed
                )
            }
        }

        // Identify currently running foreground processes
        val runningForegroundPackages = mutableSetOf<String>()
        activityManager?.runningAppProcesses?.forEach { proc ->
            if (proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                runningForegroundPackages.addAll(proc.pkgList)
            }
        }

        val results = mutableListOf<AppUsageInfo>()

        for ((pkg, stat) in aggregatedMap) {
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val icon = try {
                    packageManager.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    ContextCompat.getDrawable(context, R.drawable.ic_apps)
                }

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val standbyBucketStr = getStandbyBucketName(pkg)
                val isCurrentlyActive = runningForegroundPackages.contains(pkg)

                results.add(
                    AppUsageInfo(
                        packageName = pkg,
                        appName = appName,
                        icon = icon,
                        foregroundTimeMs = stat.totalTimeInForeground,
                        lastTimeUsed = stat.lastTimeUsed,
                        standbyBucket = standbyBucketStr,
                        isSystemApp = isSystem,
                        isCurrentlyActive = isCurrentlyActive
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Application uninstalled or inaccessible system component
                continue
            } catch (e: Exception) {
                continue
            }
        }

        return results
    }

    /**
     * Resolves the OS Standby Bucket tier for a given package on Android 9+ (API 28+).
     */
    private fun getStandbyBucketName(packageName: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && usageStatsManager != null) {
            return try {
                when (usageStatsManager.getAppStandbyBucket(packageName)) {
                    UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
                    UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
                    UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
                    UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
                    UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "RESTRICTED"
                    else -> "NEVER"
                }
            } catch (e: Exception) {
                "ACTIVE"
            }
        }
        return "NORMAL"
    }

    private data class AggregatedStat(
        val packageName: String,
        var totalTimeInForeground: Long,
        var lastTimeUsed: Long
    )
}
