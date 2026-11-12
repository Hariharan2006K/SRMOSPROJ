package com.voltwise.powermonitor.data.repository

import android.content.Context
import com.voltwise.powermonitor.data.AppDatabase
import com.voltwise.powermonitor.data.entity.AppActivityLogEntity
import com.voltwise.powermonitor.data.entity.BatteryLogEntity
import com.voltwise.powermonitor.data.entity.MonitoringSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PowerRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val batteryDao = db.batteryDao()
    private val appActivityDao = db.appActivityDao()
    private val sessionDao = db.monitoringSessionDao()

    // Battery Telemetry
    val latestSnapshot: Flow<BatteryLogEntity?> = batteryDao.getLatestSnapshot()
    val recentSnapshots: Flow<List<BatteryLogEntity>> = batteryDao.getRecentLogs(100)

    fun getLogsBetween(startTime: Long, endTime: Long): Flow<List<BatteryLogEntity>> {
        return batteryDao.getLogsBetween(startTime, endTime)
    }

    suspend fun insertBatterySnapshot(snapshot: BatteryLogEntity): Long = withContext(Dispatchers.IO) {
        batteryDao.insert(snapshot)
    }

    suspend fun getLogCount(): Int = withContext(Dispatchers.IO) {
        batteryDao.getLogCount()
    }

    // App Activities & Attribution
    fun getAppActivities(startTime: Long, endTime: Long): Flow<List<AppActivityLogEntity>> {
        return appActivityDao.getAppActivitiesBetween(startTime, endTime)
    }

    fun getTopConsumingApps(startTime: Long, endTime: Long, limit: Int = 10): Flow<List<AppActivityLogEntity>> {
        return appActivityDao.getTopConsumingApps(startTime, endTime, limit)
    }

    suspend fun insertAppActivities(logs: List<AppActivityLogEntity>) = withContext(Dispatchers.IO) {
        appActivityDao.insertAll(logs)
    }

    // Sessions
    val allSessions: Flow<List<MonitoringSessionEntity>> = sessionDao.getAllSessions()

    suspend fun startSession(startLevel: Int): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val session = MonitoringSessionEntity(
            startTime = now,
            endTime = now,
            startLevel = startLevel,
            endLevel = startLevel,
            levelDrop = 0,
            avgCurrentMa = 0f,
            peakTemperature = 0f,
            activeAppCount = 0
        )
        sessionDao.insert(session)
    }

    suspend fun updateSession(session: MonitoringSessionEntity) = withContext(Dispatchers.IO) {
        sessionDao.update(session)
    }

    suspend fun getLatestSession(): MonitoringSessionEntity? = withContext(Dispatchers.IO) {
        sessionDao.getLatestSession()
    }

    // Export & Wipe
    suspend fun getAllBatteryLogsForExport(): List<BatteryLogEntity> = withContext(Dispatchers.IO) {
        batteryDao.getAllLogsSync()
    }

    suspend fun getAllAppLogsForExport(): List<AppActivityLogEntity> = withContext(Dispatchers.IO) {
        appActivityDao.getAllAppLogsSync()
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        batteryDao.clearAll()
        appActivityDao.clearAll()
        sessionDao.clearAll()
    }
}
