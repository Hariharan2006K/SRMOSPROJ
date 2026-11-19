package com.voltwise.powermonitor.ui.diagnostics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.voltwise.powermonitor.R
import com.voltwise.powermonitor.battery.BatteryHealth
import com.voltwise.powermonitor.battery.BatteryTelemetryManager
import com.voltwise.powermonitor.data.repository.PowerRepository
import com.voltwise.powermonitor.databinding.FragmentDiagnosticsBinding
import com.voltwise.powermonitor.estimator.EcoImpactCalculator
import com.voltwise.powermonitor.estimator.PowerAttributionEngine
import com.voltwise.powermonitor.estimator.ThermalAnalyticsEngine
import com.voltwise.powermonitor.estimator.ThermalRiskLevel
import com.voltwise.powermonitor.usage.AppUsageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiagnosticsFragment : Fragment() {

    private var _binding: FragmentDiagnosticsBinding? = null
    private val binding get() = _binding!!

    private lateinit var telemetryManager: BatteryTelemetryManager
    private lateinit var usageManager: AppUsageManager
    private lateinit var repository: PowerRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        telemetryManager = BatteryTelemetryManager(requireContext())
        usageManager = AppUsageManager(requireContext())
        repository = PowerRepository(requireContext())

        evaluateDiagnostics()
    }

    private fun evaluateDiagnostics() {
        val snapshot = telemetryManager.getBatterySnapshot()

        // 1. Thermal Throttling Predictor
        val thermalReport = ThermalAnalyticsEngine.analyzeThermalState(
            currentTempCelsius = snapshot.temperatureCelsius,
            powerMw = snapshot.powerMw
        )
        binding.tvThermalRiskBadge.text = thermalReport.riskLevel.displayName
        val thermalColor = when (thermalReport.riskLevel) {
            ThermalRiskLevel.OPTIMAL -> R.color.battery_good
            ThermalRiskLevel.MODERATE -> R.color.temp_warm
            ThermalRiskLevel.HIGH_RISK -> R.color.temp_hot
            ThermalRiskLevel.CRITICAL_THROTTLING -> R.color.battery_critical
        }
        binding.tvThermalRiskBadge.setTextColor(ContextCompat.getColor(requireContext(), thermalColor))
        binding.tvThermalReportText.text = thermalReport.technicalSummary
        binding.tvThermalRecommendationText.text = thermalReport.aiRecommendation

        // 2. Eco Impact & Carbon Index
        val eco = EcoImpactCalculator.calculateEcoMetrics(snapshot.powerMw)
        binding.tvEcoGradeBadge.text = "Grade: ${eco.ecoGrade}"
        binding.tvEcoGradeBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.battery_good))
        binding.tvEcoMetricsText.text = "Hourly Intake: ${String.format("%.2f", eco.hourlyEnergyWh)} Wh | Daily Grid CO₂ Footprint: ${String.format("%.1f", eco.estimatedCarbonGramsDaily)}g"
        binding.tvEcoAdviceText.text = eco.ecoInsight

        // 3. Hardware Battery Health Evaluation
        val healthScoreText: String
        val healthColor: Int
        val healthAdviceText: String

        if (snapshot.health == BatteryHealth.GOOD && snapshot.temperatureCelsius < 38f) {
            healthScoreText = "OPTIMAL / HEALTHY"
            healthColor = ContextCompat.getColor(requireContext(), R.color.battery_good)
            healthAdviceText = "Battery temperature is optimal (${snapshot.temperatureCelsius}°C) with stable impedance and voltage (${snapshot.voltageMv} mV). No thermal throttling active."
        } else if (snapshot.temperatureCelsius in 38f..42f) {
            healthScoreText = "ELEVATED TEMPERATURE"
            healthColor = ContextCompat.getColor(requireContext(), R.color.temp_warm)
            healthAdviceText = "Battery temperature is slightly high (${snapshot.temperatureCelsius}°C). Consider reducing heavy CPU/GPU tasks or fast charging until the device cools down."
        } else if (snapshot.temperatureCelsius > 42f || snapshot.health == BatteryHealth.OVERHEAT) {
            healthScoreText = "THERMAL WARNING: OVERHEATING"
            healthColor = ContextCompat.getColor(requireContext(), R.color.battery_critical)
            healthAdviceText = "Battery temperature is critically elevated (${snapshot.temperatureCelsius}°C). Unplug the charger and close resource-heavy apps immediately to protect chemical health."
        } else {
            healthScoreText = "STATUS: ${snapshot.health.name}"
            healthColor = ContextCompat.getColor(requireContext(), R.color.battery_moderate)
            healthAdviceText = "Battery health status reported by Android HAL: ${snapshot.health.name}."
        }

        binding.tvHealthScore.text = healthScoreText
        binding.tvHealthScore.setTextColor(healthColor)
        binding.tvHealthAdvice.text = healthAdviceText

        // 4. Power Hogs Anomaly Detection
        viewLifecycleOwner.lifecycleScope.launch {
            val hogs = withContext(Dispatchers.IO) {
                if (usageManager.hasUsagePermission()) {
                    val now = System.currentTimeMillis()
                    val rawApps = usageManager.queryAppUsage(now - 24 * 3600 * 1000L, now)
                    val logs = repository.getAllBatteryLogsForExport()
                    val attributed = PowerAttributionEngine.attributePower(rawApps, logs, 24 * 3600 * 1000L)
                    PowerAttributionEngine.detectPowerHogs(attributed)
                } else {
                    emptyList()
                }
            }

            binding.layoutPowerHogContainer.removeAllViews()

            if (hogs.isEmpty()) {
                val tv = TextView(requireContext()).apply {
                    text = getString(R.string.no_power_hogs_detected)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_dark))
                    textSize = 12f
                }
                binding.layoutPowerHogContainer.addView(tv)
            } else {
                for (hog in hogs) {
                    val hogCard = LayoutInflater.from(requireContext()).inflate(R.layout.item_app_usage, binding.layoutPowerHogContainer, false)
                    val tvName = hogCard.findViewById<TextView>(R.id.tvAppName)
                    val tvStandby = hogCard.findViewById<TextView>(R.id.tvStandbyBucket)
                    val tvDuration = hogCard.findViewById<TextView>(R.id.tvForegroundDuration)
                    val tvMah = hogCard.findViewById<TextView>(R.id.tvPowerMah)
                    val tvPercent = hogCard.findViewById<TextView>(R.id.tvPowerPercentage)

                    tvName.text = hog.appName
                    tvStandby.text = hog.severity.name
                    tvStandby.setTextColor(ContextCompat.getColor(requireContext(), R.color.battery_critical))
                    tvDuration.text = hog.reason
                    tvMah.text = "ALERT"
                    tvMah.setTextColor(ContextCompat.getColor(requireContext(), R.color.battery_critical))
                    tvPercent.visibility = View.GONE

                    binding.layoutPowerHogContainer.addView(hogCard)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        evaluateDiagnostics()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
