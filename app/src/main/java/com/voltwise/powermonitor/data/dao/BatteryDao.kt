package com.voltwise.powermonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voltwise.powermonitor.data.entity.BatteryLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: BatteryLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<BatteryLogEntity>)

    @Query("SELECT * FROM battery_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<BatteryLogEntity>>

    @Query("SELECT * FROM battery_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getLogsBetween(startTime: Long, endTime: Long): Flow<List<BatteryLogEntity>>

    @Query("SELECT * FROM battery_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getLogsBetweenSync(startTime: Long, endTime: Long): List<BatteryLogEntity>

    @Query("SELECT * FROM battery_logs ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSnapshot(): Flow<BatteryLogEntity?>

    @Query("SELECT * FROM battery_logs ORDER BY timestamp ASC")
    suspend fun getAllLogsSync(): List<BatteryLogEntity>

    @Query("SELECT COUNT(*) FROM battery_logs")
    suspend fun getLogCount(): Int

    @Query("DELETE FROM battery_logs")
    suspend fun clearAll()
}
