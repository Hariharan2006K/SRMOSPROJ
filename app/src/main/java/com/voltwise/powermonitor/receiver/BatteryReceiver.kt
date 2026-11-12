package com.voltwise.powermonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voltwise.powermonitor.battery.BatteryTelemetryManager
import com.voltwise.powermonitor.data.entity.BatteryLogEntity
import com.voltwise.powermonitor.data.repository.PowerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == Intent.ACTION_BATTERY_LOW ||
            action == Intent.ACTION_BATTERY_OKAY) {

            // Take an instantaneous event log
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val telemetry = BatteryTelemetryManager(context)
                    val repository = PowerRepository(context)
                    val snapshot = telemetry.getBatterySnapshot()

                    val log = BatteryLogEntity(
                        timestamp = snapshot.timestamp,
                        level = snapshot.level,
                        voltageMv = snapshot.voltageMv,
                        temperatureCelsius = snapshot.temperatureCelsius,
                        currentMa = snapshot.currentNowMa,
                        powerMw = snapshot.powerMw,
                        status = snapshot.status.name,
                        plugType = snapshot.plugType.name,
                        screenOn = snapshot.isScreenOn
                    )
                    repository.insertBatterySnapshot(log)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
