package com.voltwise.powermonitor.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.voltwise.powermonitor.battery.BatteryTelemetryManager
import com.voltwise.powermonitor.data.entity.AppActivityLogEntity
import com.voltwise.powermonitor.data.entity.BatteryLogEntity
import com.voltwise.powermonitor.data.entity.MonitoringSessionEntity
import com.voltwise.powermonitor.data.repository.PowerRepository
import com.voltwise.powermonitor.estimator.PowerAttributionEngine
import com.voltwise.powermonitor.usage.AppUsageManager
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.max

class BatteryMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var telemetryManager: BatteryTelemetryManager
    private lateinit var usageManager: AppUsageManager
    private lateinit var repository: PowerRepository
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentSessionId: Long = -1L
    private var sessionStartLevel: Int = -1
    private var sessionStartTime: Long = -1L
    private var samplingIntervalMs: Long = DEFAULT_INTERVAL_MS

    override fun onCreate() {
        super.onCreate()
        telemetryManager = BatteryTelemetryManager(this)
        usageManager = AppUsageManager(this)
        repository = PowerRepository(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoltWise:MonitorWakeLock")

        BatteryNotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                startMonitoring()
            }
            ACTION_STOP -> {
                stopMonitoring()
            }
        }

        return START_STICKY
    }

    private fun startMonitoring() {
        isRunning = true

        // Display initial foreground notification
        val initialSnapshot = telemetryManager.getBatterySnapshot()
        startForeground(
            BatteryNotificationHelper.NOTIFICATION_ID,
            BatteryNotificationHelper.buildNotification(this, initialSnapshot)
        )

        // Launch monitoring loop
        serviceScope.launch {
            sessionStartTime = System.currentTimeMillis()
            sessionStartLevel = initialSnapshot.level
            currentSessionId = repository.startSession(sessionStartLevel)

            var cycleCount = 0

            while (isActive) {
                sampleAndRecordTelemetry()

                // Every 6 cycles (~30 minutes on 5-minute interval), snapshot app usage logs into DB
                cycleCount++
                if (cycleCount % 6 == 0) {
                    recordAppUsageSnapshot()
                    updateCurrentSession()
                }

                delay(samplingIntervalMs)
            }
        }
    }

    private suspend fun sampleAndRecordTelemetry() {
        try {
            wakeLock?.acquire(3000L) // Safe partial wakelock for snapshot execution

            val snapshot = telemetryManager.getBatterySnapshot()

            val logEntity = BatteryLogEntity(
                timestamp = snapshot.timestamp,
                level = snapshot.level,
                voltageMv = snapshot.voltageMv,
                temperatureCelsius = snapshot.temperatureCelsius,
                currentMa = snapshot.currentNowMa,
                powerMw = snapshot.powerMw,
                status = snapshot.status.name,
                plugType = snapshot.plugType.name,
                screenOn = snapshot.isScreenOn
            )

            repository.insertBatterySnapshot(logEntity)

            // Update Ongoing Foreground Notification
            val notification = BatteryNotificationHelper.buildNotification(this, snapshot)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(BatteryNotificationHelper.NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private suspend fun recordAppUsageSnapshot() {
        if (!usageManager.hasUsagePermission()) return

        val now = System.currentTimeMillis()
        val windowStart = now - (60 * 60 * 1000L) // Last 1 hour
        val rawApps = usageManager.queryAppUsage(windowStart, now)
        val recentLogs = repository.getAllBatteryLogsForExport()
        val attributedApps = PowerAttributionEngine.attributePower(rawApps, recentLogs, 60 * 60 * 1000L)

        val entities = attributedApps.map { app ->
            AppActivityLogEntity(
                timestamp = now,
                packageName = app.packageName,
                appName = app.appName,
                foregroundDurationMs = app.foregroundTimeMs,
                lastTimeUsed = app.lastTimeUsed,
                estimatedPowerMah = app.estimatedPowerMah,
                percentageOfTotal = app.percentageOfTotal,
                standbyBucket = app.standbyBucket,
                isSystemApp = app.isSystemApp
            )
        }

        repository.insertAppActivities(entities)
    }

    private suspend fun updateCurrentSession() {
        if (currentSessionId <= 0) return
        val currentSnapshot = telemetryManager.getBatterySnapshot()
        val now = System.currentTimeMillis()
        val drop = max(0, sessionStartLevel - currentSnapshot.level)

        val updatedSession = MonitoringSessionEntity(
            id = currentSessionId,
            startTime = sessionStartTime,
            endTime = now,
            startLevel = sessionStartLevel,
            endLevel = currentSnapshot.level,
            levelDrop = drop,
            avgCurrentMa = abs(currentSnapshot.currentAvgMa),
            peakTemperature = currentSnapshot.temperatureCelsius,
            activeAppCount = 0
        )
        repository.updateSession(updatedSession)
    }

    private fun stopMonitoring() {
        isRunning = false
        serviceScope.launch {
            updateCurrentSession()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.voltwise.powermonitor.action.START"
        const val ACTION_STOP = "com.voltwise.powermonitor.action.STOP"
        const val DEFAULT_INTERVAL_MS = 60_000L // 1 minute default interval

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
