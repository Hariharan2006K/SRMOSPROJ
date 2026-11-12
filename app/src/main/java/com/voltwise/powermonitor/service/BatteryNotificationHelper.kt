package com.voltwise.powermonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.voltwise.powermonitor.R
import com.voltwise.powermonitor.battery.BatteryInfo
import com.voltwise.powermonitor.ui.MainActivity

object BatteryNotificationHelper {

    const val CHANNEL_ID = "voltwise_monitor_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notif_channel_name)
            val desc = context.getString(R.string.notif_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(context: Context, info: BatteryInfo?): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val contentText = if (info != null) {
            val currentFormat = String.format("%.0f", info.currentNowMa)
            val powerFormat = String.format("%.0f", info.powerMw)
            val tempFormat = String.format("%.1f", info.temperatureCelsius)
            "${info.level}% • ${info.status.name} • ${powerFormat} mW • ${tempFormat}°C"
        } else {
            "Continuous Power Monitoring Active"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("VoltWise Power Monitor")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_battery_std)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
