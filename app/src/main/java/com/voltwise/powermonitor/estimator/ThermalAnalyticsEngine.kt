package com.voltwise.powermonitor.estimator

import kotlin.math.max

enum class ThermalRiskLevel(val displayName: String, val severityColorHex: String) {
    OPTIMAL("Optimal", "#4CAF50"),
    MODERATE("Moderate Warmth", "#FFC107"),
    HIGH_RISK("High Risk", "#FF9800"),
    CRITICAL_THROTTLING("Critical Throttling", "#F44336")
}

data class ThermalReport(
    val tempCelsius: Float,
    val rampRatePerMin: Float,
    val riskLevel: ThermalRiskLevel,
    val degradationIndex: Float, // 0 to 100%
    val throttlingEtaMinutes: Int?, // null if not heating up
    val technicalSummary: String,
    val aiRecommendation: String
)

/**
 * Enterprise Thermodynamic & Lithium Thermal Degradation Analytics Engine.
 * Predicts thermal throttling ETAs and tracks chemical battery stress.
 */
object ThermalAnalyticsEngine {

    const val THERMAL_THROTTLING_THRESHOLD_C = 42.0f
    const val CRITICAL_DAMAGE_THRESHOLD_C = 48.0f

    fun analyzeThermalState(
        currentTempCelsius: Float,
        prevTempCelsius: Float? = null,
        timeDeltaSeconds: Float = 60f,
        powerMw: Float = 0f
    ): ThermalReport {
        val rampRate = if (prevTempCelsius != null && timeDeltaSeconds > 0) {
            ((currentTempCelsius - prevTempCelsius) / timeDeltaSeconds) * 60f
        } else {
            0.0f
        }

        val riskLevel = when {
            currentTempCelsius >= CRITICAL_DAMAGE_THRESHOLD_C -> ThermalRiskLevel.CRITICAL_THROTTLING
            currentTempCelsius >= THERMAL_THROTTLING_THRESHOLD_C || rampRate > 1.5f -> ThermalRiskLevel.HIGH_RISK
            currentTempCelsius >= 37.0f || rampRate > 0.5f -> ThermalRiskLevel.MODERATE
            else -> ThermalRiskLevel.OPTIMAL
        }

        val throttlingEta = if (rampRate > 0.1f && currentTempCelsius < THERMAL_THROTTLING_THRESHOLD_C) {
            val neededRise = THERMAL_THROTTLING_THRESHOLD_C - currentTempCelsius
            (neededRise / rampRate).toInt().coerceAtLeast(1)
        } else if (currentTempCelsius >= THERMAL_THROTTLING_THRESHOLD_C) {
            0
        } else {
            null
        }

        // Degradation Stress Index formula based on Arrhenius equation dynamics
        val baseStress = (currentTempCelsius - 25.0f).coerceAtLeast(0f) * 2.2f
        val powerStress = (powerMw / 1000f).coerceAtLeast(0f) * 1.5f
        val degradationIndex = (baseStress + powerStress).coerceIn(0f, 100f)

        val (summary, recommendation) = when (riskLevel) {
            ThermalRiskLevel.OPTIMAL -> Pair(
                "Thermodynamic state is within optimal operating limits (20°C - 35°C). Electrolyte degradation rate is minimal.",
                "Battery chemical stability is maximum. Ideal condition for long-term cycle retention."
            )
            ThermalRiskLevel.MODERATE -> Pair(
                "Elevated operating temperature detected. Chemical reaction speed increases, slightly accelerating internal impedance growth.",
                "Avoid holding device on soft insulating surfaces while active. Consider closing heavy background tasks."
            )
            ThermalRiskLevel.HIGH_RISK -> Pair(
                "High thermal stress ($currentTempCelsius°C). Approaching SoC CPU thermal mitigation threshold.",
                "Reduce screen brightness and pause fast-charging or gaming to prevent automatic CPU throttling."
            )
            ThermalRiskLevel.CRITICAL_THROTTLING -> Pair(
                "CRITICAL THERMAL LEVEL ($currentTempCelsius°C)! System kernel will throttle clock frequency to prevent hardware damage.",
                "Immediate cooling advised. Disconnect charger immediately and let the device rest in ambient shade."
            )
        }

        return ThermalReport(
            tempCelsius = currentTempCelsius,
            rampRatePerMin = rampRate,
            riskLevel = riskLevel,
            degradationIndex = degradationIndex,
            throttlingEtaMinutes = throttlingEta,
            technicalSummary = summary,
            aiRecommendation = recommendation
        )
    }
}
