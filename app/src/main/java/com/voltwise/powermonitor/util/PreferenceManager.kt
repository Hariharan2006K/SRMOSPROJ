package com.voltwise.powermonitor.util

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voltwise_prefs", Context.MODE_PRIVATE)

    var samplingIntervalMs: Long
        get() = prefs.getLong(KEY_INTERVAL, 60_000L) // Default 1 min
        set(value) = prefs.edit().putLong(KEY_INTERVAL, value).apply()

    var isAutoStartOnBootEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BOOT, value).apply()

    var filterUserAppsOnly: Boolean
        get() = prefs.getBoolean(KEY_USER_APPS_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_USER_APPS_ONLY, value).apply()

    companion object {
        private const val KEY_INTERVAL = "sampling_interval_ms"
        private const val KEY_AUTO_BOOT = "auto_start_boot"
        private const val KEY_USER_APPS_ONLY = "filter_user_apps"
    }
}
