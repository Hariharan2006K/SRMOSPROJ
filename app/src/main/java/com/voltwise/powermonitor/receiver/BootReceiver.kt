package com.voltwise.powermonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voltwise.powermonitor.service.BatteryMonitorService
import com.voltwise.powermonitor.util.PreferenceManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = PreferenceManager(context)
            if (prefs.isAutoStartOnBootEnabled) {
                BatteryMonitorService.startService(context)
            }
        }
    }
}
