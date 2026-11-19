package com.voltwise.powermonitor.estimator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThermalAnalyticsEngineTest {

    @Test
    fun testOptimalThermalState() {
        val report = ThermalAnalyticsEngine.analyzeThermalState(
            currentTempCelsius = 28.5f,
            powerMw = 800f
        )
        assertEquals(ThermalRiskLevel.OPTIMAL, report.riskLevel)
        assertEquals(28.5f, report.tempCelsius, 0.01f)
    }

    @Test
    fun testCriticalThermalState() {
        val report = ThermalAnalyticsEngine.analyzeThermalState(
            currentTempCelsius = 49.0f,
            powerMw = 3500f
        )
        assertEquals(ThermalRiskLevel.CRITICAL_THROTTLING, report.riskLevel)
        assertEquals(0, report.throttlingEtaMinutes)
    }

    @Test
    fun testThermalRampRateCalculation() {
        val report = ThermalAnalyticsEngine.analyzeThermalState(
            currentTempCelsius = 38.0f,
            prevTempCelsius = 36.0f,
            timeDeltaSeconds = 60f,
            powerMw = 2000f
        )
        assertEquals(2.0f, report.rampRatePerMin, 0.01f)
        assertEquals(ThermalRiskLevel.HIGH_RISK, report.riskLevel)
    }
}
