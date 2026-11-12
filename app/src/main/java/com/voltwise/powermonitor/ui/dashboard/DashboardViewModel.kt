package com.voltwise.powermonitor.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voltwise.powermonitor.battery.BatteryInfo
import com.voltwise.powermonitor.battery.BatteryTelemetryManager
import com.voltwise.powermonitor.data.repository.PowerRepository
import com.voltwise.powermonitor.service.BatteryMonitorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val telemetryManager = BatteryTelemetryManager(application)
    private val repository = PowerRepository(application)

    private val _batteryInfo = MutableStateFlow<BatteryInfo?>(null)
    val batteryInfo: StateFlow<BatteryInfo?> = _batteryInfo.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(BatteryMonitorService.isRunning)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    init {
        // Poll instantaneous telemetry every 2 seconds when user is viewing dashboard
        viewModelScope.launch {
            while (isActive) {
                _batteryInfo.value = telemetryManager.getBatterySnapshot()
                _isServiceRunning.value = BatteryMonitorService.isRunning
                delay(2000L)
            }
        }
    }

    fun toggleService() {
        val app = getApplication<Application>()
        if (BatteryMonitorService.isRunning) {
            BatteryMonitorService.stopService(app)
            _isServiceRunning.value = false
        } else {
            BatteryMonitorService.startService(app)
            _isServiceRunning.value = true
        }
    }

    fun refreshTelemetry() {
        _batteryInfo.value = telemetryManager.getBatterySnapshot()
        _isServiceRunning.value = BatteryMonitorService.isRunning
    }
}
