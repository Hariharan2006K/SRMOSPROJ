package com.voltwise.powermonitor.ui.history

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voltwise.powermonitor.data.entity.BatteryLogEntity
import com.voltwise.powermonitor.data.entity.MonitoringSessionEntity
import com.voltwise.powermonitor.data.repository.PowerRepository
import com.voltwise.powermonitor.util.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PowerRepository(application)

    val recentSnapshots: StateFlow<List<BatteryLogEntity>> = repository.recentSnapshots
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sessions: StateFlow<List<MonitoringSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _snapshotCount = MutableStateFlow(0)
    val snapshotCount: StateFlow<Int> = _snapshotCount.asStateFlow()

    private val _dischargeRateText = MutableStateFlow("~0.0% / hr")
    val dischargeRateText: StateFlow<String> = _dischargeRateText.asStateFlow()

    init {
        refreshAnalytics()
    }

    fun refreshAnalytics() {
        viewModelScope.launch {
            val count = repository.getLogCount()
            _snapshotCount.value = count

            withContext(Dispatchers.IO) {
                val logs = repository.getAllBatteryLogsForExport()
                if (logs.size >= 2) {
                    val first = logs.first()
                    val last = logs.last()
                    val durationHours = (last.timestamp - first.timestamp) / (1000f * 3600f)
                    val drop = first.level - last.level

                    if (durationHours > 0.05f && drop > 0) {
                        val rate = drop / durationHours
                        _dischargeRateText.value = String.format("~%.1f%% / hr", rate)
                    } else {
                        // Calculate from average current draw: Current (mA) / Capacity (4500 mAh) * 100
                        val discharging = logs.filter { it.currentMa < 0 }
                        if (discharging.isNotEmpty()) {
                            val avgCurrent = discharging.map { abs(it.currentMa) }.average().toFloat()
                            val estRate = (avgCurrent / 4500f) * 100f
                            _dischargeRateText.value = String.format("~%.1f%% / hr", estRate)
                        }
                    }
                }
            }
        }
    }

    suspend fun exportCsv(): Intent? {
        return LogExporter.exportHistoryToCsv(getApplication(), repository)
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearAllHistory()
            refreshAnalytics()
        }
    }
}
