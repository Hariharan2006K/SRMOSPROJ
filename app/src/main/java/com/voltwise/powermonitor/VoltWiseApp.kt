package com.voltwise.powermonitor

import android.app.Application
import com.voltwise.powermonitor.data.repository.PowerRepository
import com.voltwise.powermonitor.service.BatteryNotificationHelper

class VoltWiseApp : Application() {

    lateinit var repository: PowerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = PowerRepository(this)
        BatteryNotificationHelper.createNotificationChannel(this)
    }

    companion object {
        lateinit var instance: VoltWiseApp
            private set
    }
}
