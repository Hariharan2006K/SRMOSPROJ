package com.voltwise.powermonitor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.voltwise.powermonitor.data.dao.AppActivityDao
import com.voltwise.powermonitor.data.dao.BatteryDao
import com.voltwise.powermonitor.data.dao.MonitoringSessionDao
import com.voltwise.powermonitor.data.entity.AppActivityLogEntity
import com.voltwise.powermonitor.data.entity.BatteryLogEntity
import com.voltwise.powermonitor.data.entity.MonitoringSessionEntity

@Database(
    entities = [
        BatteryLogEntity::class,
        AppActivityLogEntity::class,
        MonitoringSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao
    abstract fun appActivityDao(): AppActivityDao
    abstract fun monitoringSessionDao(): MonitoringSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voltwise_power_monitor.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
