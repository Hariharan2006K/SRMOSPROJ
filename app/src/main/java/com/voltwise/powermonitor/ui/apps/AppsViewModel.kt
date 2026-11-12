package com.voltwise.powermonitor.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voltwise.powermonitor.data.repository.PowerRepository
import com.voltwise.powermonitor.estimator.PowerAttributionEngine
import com.voltwise.powermonitor.usage.AppUsageInfo
import com.voltwise.powermonitor.usage.AppUsageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val usageManager = AppUsageManager(application)
    private val repository = PowerRepository(application)

    private val _appsList = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val appsList: StateFlow<List<AppUsageInfo>> = _appsList.asStateFlow()

    private val _totalEnergyDrained = MutableStateFlow(0f)
    val totalEnergyDrained: StateFlow<Float> = _totalEnergyDrained.asStateFlow()

    private val _hasUsagePermission = MutableStateFlow(usageManager.hasUsagePermission())
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    var selectedRange: TimeRange = TimeRange.HOURS_24
        private set

    var selectedSort: SortCriteria = SortCriteria.POWER
        private set

    var userAppsOnly: Boolean = true
        private set

    init {
        loadData()
    }

    fun setTimeRange(range: TimeRange) {
        if (selectedRange != range) {
            selectedRange = range
            loadData()
        }
    }

    fun setSortCriteria(sort: SortCriteria) {
        selectedSort = sort
        applyFilterAndSort()
    }

    fun toggleUserAppsOnly(onlyUser: Boolean) {
        userAppsOnly = onlyUser
        applyFilterAndSort()
    }

    fun checkPermissions() {
        _hasUsagePermission.value = usageManager.hasUsagePermission()
    }

    private var rawAttributedApps: List<AppUsageInfo> = emptyList()

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _hasUsagePermission.value = usageManager.hasUsagePermission()

            if (!_hasUsagePermission.value) {
                _appsList.value = emptyList()
                _isLoading.value = false
                return@launch
            }

            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val windowMs = selectedRange.durationMs
                val startTime = now - windowMs

                val rawApps = usageManager.queryAppUsage(startTime, now)
                val recentLogs = repository.getAllBatteryLogsForExport()

                val attributed = PowerAttributionEngine.attributePower(rawApps, recentLogs, windowMs)
                rawAttributedApps = attributed

                val totalMah = attributed.sumOf { it.estimatedPowerMah.toDouble() }.toFloat()
                _totalEnergyDrained.value = totalMah
            }

            applyFilterAndSort()
            _isLoading.value = false
        }
    }

    private fun applyFilterAndSort() {
        var filtered = rawAttributedApps
        if (userAppsOnly) {
            filtered = filtered.filter { !it.isSystemApp }
        }

        val sorted = when (selectedSort) {
            SortCriteria.POWER -> filtered.sortedByDescending { it.estimatedPowerMah }
            SortCriteria.DURATION -> filtered.sortedByDescending { it.foregroundTimeMs }
            SortCriteria.NAME -> filtered.sortedBy { it.appName.lowercase() }
        }

        _appsList.value = sorted
    }

    enum class TimeRange(val durationMs: Long) {
        HOUR_1(1 * 3600 * 1000L),
        HOURS_6(6 * 3600 * 1000L),
        HOURS_24(24 * 3600 * 1000L),
        DAYS_7(7 * 24 * 3600 * 1000L)
    }

    enum class SortCriteria {
        POWER,
        DURATION,
        NAME
    }
}
