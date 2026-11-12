package com.voltwise.powermonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.voltwise.powermonitor.data.entity.MonitoringSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoringSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: MonitoringSessionEntity): Long

    @Update
    suspend fun update(session: MonitoringSessionEntity)

    @Query("SELECT * FROM monitoring_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<MonitoringSessionEntity>>

    @Query("SELECT * FROM monitoring_sessions ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSession(): MonitoringSessionEntity?

    @Query("SELECT * FROM monitoring_sessions ORDER BY startTime ASC")
    suspend fun getAllSessionsSync(): List<MonitoringSessionEntity>

    @Query("DELETE FROM monitoring_sessions")
    suspend fun clearAll()
}
