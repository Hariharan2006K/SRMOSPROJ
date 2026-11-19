package com.voltwise.powermonitor.estimator

data class EcoMetrics(
    val powerMw: Float,
    val hourlyEnergyWh: Float,
    val dailyEnergyWh: Float,
    val estimatedCarbonGramsDaily: Float,
    val ecoGrade: String,
    val ecoInsight: String
)

/**
 * Enterprise Carbon Footprint & Energy Efficiency Metric Calculator.
 */
object EcoImpactCalculator {

    // Global average electricity carbon intensity: ~0.42 kg CO2 per kWh (420mg CO2 per Wh)
    private const val CO2_GRAMS_PER_WH = 0.42f

    fun calculateEcoMetrics(powerMw: Float): EcoMetrics {
        val hourlyEnergyWh = (powerMw / 1000f).coerceAtLeast(0f)
        val dailyEnergyWh = hourlyEnergyWh * 24f
        val dailyCarbonGrams = dailyEnergyWh * CO2_GRAMS_PER_WH

        val (grade, insight) = when {
            powerMw <= 800f -> Pair(
                "A+",
                "Ultra-efficient power profile. Extremely low environmental footprint and minimal energy waste."
            )
            powerMw <= 1500f -> Pair(
                "A",
                "Balanced energy utilization. Battery consumption is within sustainable green benchmarks."
            )
            powerMw <= 2500f -> Pair(
                "B",
                "Moderate energy consumption. Display or active network radio is drawing significant power."
            )
            powerMw <= 4000f -> Pair(
                "C",
                "High power draw profile ($powerMw mW). Accelerating charging frequency and grid carbon intake."
            )
            else -> Pair(
                "F",
                "HEAVY DRAIN RATE! Power draw exceeds 4,000 mW. High thermal loss and energy overhead."
            )
        }

        return EcoMetrics(
            powerMw = powerMw,
            hourlyEnergyWh = hourlyEnergyWh,
            dailyEnergyWh = dailyEnergyWh,
            estimatedCarbonGramsDaily = dailyCarbonGrams,
            ecoGrade = grade,
            ecoInsight = insight
        )
    }
}
