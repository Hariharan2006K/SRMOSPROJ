package com.voltwise.powermonitor.estimator

import org.junit.Assert.assertEquals
import org.junit.Test

class EcoImpactCalculatorTest {

    @Test
    fun testLowPowerEcoMetrics() {
        val eco = EcoImpactCalculator.calculateEcoMetrics(600f)
        assertEquals("A+", eco.ecoGrade)
        assertEquals(0.6f, eco.hourlyEnergyWh, 0.01f)
    }

    @Test
    fun testHighPowerEcoMetrics() {
        val eco = EcoImpactCalculator.calculateEcoMetrics(4500f)
        assertEquals("F", eco.ecoGrade)
        assertEquals(4.5f, eco.hourlyEnergyWh, 0.01f)
    }
}
