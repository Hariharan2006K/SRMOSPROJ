package com.voltwise.powermonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voltwise.powermonitor.data.entity.AppActivityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AppActivityLogEntity>)

    @Query("SELECT * FROM app_activity_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY estimatedPowerMah DESC")
    fun getAppActivitiesBetween(startTime: Long, endTime: Long): Flow<List<AppActivityLogEntity>>

    @Query("SELECT * FROM app_activity_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY estimatedPowerMah DESC LIMIT :limit")
    fun getTopConsumingApps(startTime: Long, endTime: Long, limit: Int = 10): Flow<List<AppActivityLogEntity>>

    @Query("SELECT * FROM app_activity_logs ORDER BY timestamp ASC")
    suspend fun getAllAppLogsSync(): List<AppActivityLogEntity>

    @Query("DELETE FROM app_activity_logs")
    suspend fun clearAll()
}
