package com.voltwise.powermonitor.estimator

import com.voltwise.powermonitor.data.entity.BatteryLogEntity
import com.voltwise.powermonitor.usage.AppUsageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerAttributionEngineTest {

    @Test
    fun testPowerAttributionCalculation() {
        val app1 = AppUsageInfo(
            packageName = "com.test.video",
            appName = "Video Player",
            icon = null,
            foregroundTimeMs = 3600 * 1000L, // 1 hour
            lastTimeUsed = System.currentTimeMillis(),
            standbyBucket = "ACTIVE",
            isSystemApp = false
        )

        val app2 = AppUsageInfo(
            packageName = "com.test.notes",
            appName = "Notes",
            icon = null,
            foregroundTimeMs = 1800 * 1000L, // 30 mins
            lastTimeUsed = System.currentTimeMillis(),
            standbyBucket = "WORKING_SET",
            isSystemApp = false
        )

        val now = System.currentTimeMillis()
        val mockLogs = listOf(
            BatteryLogEntity(
                timestamp = now - 3600 * 1000L,
                level = 100,
                voltageMv = 4200,
                temperatureCelsius = 30f,
                currentMa = -400f,
                powerMw = 1680f,
                status = "DISCHARGING",
                plugType = "BATTERY",
                screenOn = true
            ),
            BatteryLogEntity(
                timestamp = now,
                level = 90, // 10% drop
                voltageMv = 3900,
                temperatureCelsius = 32f,
                currentMa = -400f,
                powerMw = 1560f,
                status = "DISCHARGING",
                plugType = "BATTERY",
                screenOn = true
            )
        )

        val results = PowerAttributionEngine.attributePower(
            apps = listOf(app1, app2),
            batteryLogs = mockLogs,
            timeWindowMs = 3600 * 1000L
        )

        assertNotNull(results)
        assertEquals(2, results.size)

        val videoResult = results.find { it.packageName == "com.test.video" }!!
        val notesResult = results.find { it.packageName == "com.test.notes" }!!

        // Video Player ran for twice as long in ACTIVE bucket, so should consume more energy
        assertTrue("Video player should consume more energy than notes app", videoResult.estimatedPowerMah > notesResult.estimatedPowerMah)
        assertTrue("Percentages should be positive", videoResult.percentageOfTotal > 0f && notesResult.percentageOfTotal > 0f)
    }

    @Test
    fun testDetectPowerHogs() {
        val normalApp = AppUsageInfo(
            packageName = "com.test.calc",
            appName = "Calculator",
            icon = null,
            foregroundTimeMs = 60 * 1000L,
            lastTimeUsed = System.currentTimeMillis(),
            standbyBucket = "ACTIVE",
            isSystemApp = false,
            estimatedPowerMah = 5f,
            percentageOfTotal = 2f
        )

        val rogueApp = AppUsageInfo(
            packageName = "com.test.miner",
            appName = "Crypto Background Miner",
            icon = null,
            foregroundTimeMs = 3600 * 1000L,
            lastTimeUsed = System.currentTimeMillis(),
            standbyBucket = "ACTIVE",
            isSystemApp = false,
            estimatedPowerMah = 250f,
            percentageOfTotal = 35f
        )

        val hogs = PowerAttributionEngine.detectPowerHogs(listOf(normalApp, rogueApp))
        assertEquals(1, hogs.size)
        assertEquals("Crypto Background Miner", hogs[0].appName)
        assertEquals(HogSeverity.HIGH, hogs[0].severity)
    }
}
