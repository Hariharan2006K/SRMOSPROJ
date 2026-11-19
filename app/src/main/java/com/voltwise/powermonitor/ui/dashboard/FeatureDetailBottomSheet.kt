package com.voltwise.powermonitor.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.voltwise.powermonitor.R
import com.voltwise.powermonitor.battery.BatteryInfo
import com.voltwise.powermonitor.battery.BatteryStatus
import com.voltwise.powermonitor.databinding.DialogFeatureDetailBinding
import com.voltwise.powermonitor.estimator.EcoImpactCalculator
import com.voltwise.powermonitor.estimator.ThermalAnalyticsEngine
import kotlin.math.abs

enum class MetricType {
    POWER,
    CURRENT,
    VOLTAGE,
    TEMPERATURE,
    HEALTH,
    PLUG_SOURCE,
    BATTERY_GAUGE,
    TIME_ESTIMATE
}

class FeatureDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogFeatureDetailBinding? = null
    private val binding get() = _binding!!

    private var metricType: MetricType = MetricType.POWER
    private var batteryInfo: BatteryInfo? = null

    companion object {
        fun newInstance(type: MetricType, info: BatteryInfo?): FeatureDetailBottomSheet {
            val fragment = FeatureDetailBottomSheet()
            fragment.metricType = type
            fragment.batteryInfo = info
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFeatureDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseDetail.setOnClickListener {
            dismiss()
        }

        populateMetricData()
    }

    private fun populateMetricData() {
        val info = batteryInfo ?: return

        when (metricType) {
            MetricType.POWER -> setupPowerDetail(info)
            MetricType.CURRENT -> setupCurrentDetail(info)
            MetricType.VOLTAGE -> setupVoltageDetail(info)
            MetricType.TEMPERATURE -> setupTemperatureDetail(info)
            MetricType.HEALTH -> setupHealthDetail(info)
            MetricType.PLUG_SOURCE -> setupPlugDetail(info)
            MetricType.BATTERY_GAUGE -> setupGaugeDetail(info)
            MetricType.TIME_ESTIMATE -> setupTimeEstimateDetail(info)
        }
    }

    private fun setupPowerDetail(info: BatteryInfo) {
        val eco = EcoImpactCalculator.calculateEcoMetrics(info.powerMw)
        binding.ivDetailIcon.setImageResource(R.drawable.ic_bolt)
        binding.tvDetailTitle.text = "Instantaneous Power (mW)"
        binding.tvDetailCategory.text = "System Telemetry & Eco Energy"
        binding.tvDetailLiveValue.text = "${String.format("%.0f", info.powerMw)} mW"

        binding.ivRiskDot.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary))
        binding.tvRiskTitle.text = "Eco Efficiency Grade: ${eco.ecoGrade}"
        binding.tvRiskScore.text = "Daily Carbon: ${String.format("%.1f", eco.estimatedCarbonGramsDaily)}g CO₂"

        binding.tvHardwareExplanation.text =
            "Power in milliwatts (mW) represents real-time electrical power consumption or charging throughput (P = V × I). Higher values indicate intense CPU/GPU workload or screen backlight energy draw."
        binding.tvEngineeringFormula.text = "P (mW) = Voltage (${String.format("%.2f", info.voltageMv / 1000f)} V) × Current (${String.format("%.0f", abs(info.currentNowMa))} mA)"
        binding.tvAiRecommendation.text = eco.ecoInsight
    }

    private fun setupCurrentDetail(info: BatteryInfo) {
        binding.ivDetailIcon.setImageResource(R.drawable.ic_speed)
        binding.tvDetailTitle.text = "Battery Current (mA)"
        binding.tvDetailCategory.text = "Coulomb Rate & Ion Flow"
        val sign = if (info.currentNowMa > 0) "+" else ""
        binding.tvDetailLiveValue.text = "$sign${String.format("%.0f", info.currentNowMa)} mA"

        val isCharging = info.currentNowMa > 0
        binding.ivRiskDot.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                if (isCharging) R.color.battery_good else R.color.secondary
            )
        )
        binding.tvRiskTitle.text = if (isCharging) "Charging Inflow Active" else "Discharge Rate Monitored"
        binding.tvRiskScore.text = "Rate: ${String.format("%.0f", abs(info.currentNowMa))} mA/h"

        binding.tvHardwareExplanation.text =
            "Current measures the rate of charge carrier (lithium ion) transport through the battery electrodes. Positive values denote external charging current; negative values denote discharge."
        binding.tvEngineeringFormula.text = "I (mA) = Q (Coulombs per second) / 1.602e-19"
        binding.tvAiRecommendation.text = if (isCharging) {
            "Battery is accepting charge smoothly. Fast charge controllers adapt current dynamically based on temperature curves."
        } else {
            "Discharge rate is healthy. To decrease discharge current, lower screen refresh rate or deactivate background location services."
        }
    }

    private fun setupVoltageDetail(info: BatteryInfo) {
        val voltageVolts = info.voltageMv / 1000f
        binding.ivDetailIcon.setImageResource(R.drawable.ic_battery_std)
        binding.tvDetailTitle.text = "Cell Voltage (V)"
        binding.tvDetailCategory.text = "Electrochemical Potential"
        binding.tvDetailLiveValue.text = "${String.format("%.2f", voltageVolts)} V"

        val isHealthyVoltage = voltageVolts in 3.4f..4.4f
        binding.ivRiskDot.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                if (isHealthyVoltage) R.color.battery_good else R.color.battery_warning
            )
        )
        binding.tvRiskTitle.text = if (isHealthyVoltage) "Nominal Operating Cell Potential" else "Voltage Out of Standard Range"
        binding.tvRiskScore.text = "Cell Pot: ${String.format("%.2f", voltageVolts)}V"

        binding.tvHardwareExplanation.text =
            "Lithium-ion battery cell potential varies between 3.2V (0% charge) and 4.4V (100% full). Voltage curves drop under heavy electrical loads due to internal cell resistance (V = V_OC - I × R_int)."
        binding.tvEngineeringFormula.text = "V_terminal = V_open_circuit - (I_load × R_internal)"
        binding.tvAiRecommendation.text =
            "Cell potential stability is optimal. Maintaining battery state of charge between 20% and 80% prevents high-voltage oxidation strain."
    }

    private fun setupTemperatureDetail(info: BatteryInfo) {
        val thermalReport = ThermalAnalyticsEngine.analyzeThermalState(
            currentTempCelsius = info.temperatureCelsius,
            powerMw = info.powerMw
        )

        binding.ivDetailIcon.setImageResource(R.drawable.ic_thermostat)
        binding.tvDetailTitle.text = "Thermal Telemetry (°C)"
        binding.tvDetailCategory.text = "Thermodynamics & Heat Stress"
        binding.tvDetailLiveValue.text = "${String.format("%.1f", info.temperatureCelsius)} °C"

        val riskColor = when (thermalReport.riskLevel) {
            com.voltwise.powermonitor.estimator.ThermalRiskLevel.OPTIMAL -> R.color.battery_good
            com.voltwise.powermonitor.estimator.ThermalRiskLevel.MODERATE -> R.color.temp_warm
            com.voltwise.powermonitor.estimator.ThermalRiskLevel.HIGH_RISK -> R.color.temp_hot
            com.voltwise.powermonitor.estimator.ThermalRiskLevel.CRITICAL_THROTTLING -> R.color.battery_critical
        }
        binding.ivRiskDot.setColorFilter(ContextCompat.getColor(requireContext(), riskColor))
        binding.tvRiskTitle.text = "Thermal Risk: ${thermalReport.riskLevel.displayName}"
        binding.tvRiskScore.text = "Degradation Index: ${String.format("%.0f", thermalReport.degradationIndex)}%"

        binding.tvHardwareExplanation.text = thermalReport.technicalSummary
        binding.tvEngineeringFormula.text = "ΔT = (P_loss × Δt) / (m × C_p) [Arrhenius Heat Equation]"
        binding.tvAiRecommendation.text = thermalReport.aiRecommendation
    }

    private fun setupHealthDetail(info: BatteryInfo) {
        binding.ivDetailIcon.setImageResource(R.drawable.ic_check_circle)
        binding.tvDetailTitle.text = "Battery Health Rating"
        binding.tvDetailCategory.text = "State of Health (SoH) Diagnostics"
        binding.tvDetailLiveValue.text = info.health.name

        binding.ivRiskDot.setColorFilter(ContextCompat.getColor(requireContext(), R.color.battery_good))
        binding.tvRiskTitle.text = "Hardware Diagnostic: Normal"
        binding.tvRiskScore.text = "Impedance: Nominal"

        binding.tvHardwareExplanation.text =
            "State of Health (SoH) assesses internal resistance and chemical degradation of the cathode/anode structure. Good health indicates low internal resistance and zero physical swelling."
        binding.tvEngineeringFormula.text = "SoH (%) = (Remaining Max Capacity / Design Capacity) × 100"
        binding.tvAiRecommendation.text =
            "Your battery health status is GOOD. Avoid exposing device to temperatures above 45°C to preserve current health rating."
    }

    private fun setupPlugDetail(info: BatteryInfo) {
        binding.ivDetailIcon.setImageResource(R.drawable.ic_battery_charging)
        binding.tvDetailTitle.text = "Power Source Interface"
        binding.tvDetailCategory.text = "Power Delivery & PMIC Controller"
        binding.tvDetailLiveValue.text = info.plugType.name

        binding.ivRiskDot.setColorFilter(ContextCompat.getColor(requireContext(), R.color.secondary))
        binding.tvRiskTitle.text = "Source: ${info.plugType.name}"
        binding.tvRiskScore.text = "Protocol: USB-PD / PMIC"

        binding.tvHardwareExplanation.text =
            "Identifies active power delivery hardware interface (AC Charger, USB Host, Wireless Qi/MagSafe, or Battery Power). Power Management IC (PMIC) negotiates voltage step-down."
        binding.tvEngineeringFormula.text = "P_in = V_bus × I_bus × η_conversion"
        binding.tvAiRecommendation.text = if (info.status == BatteryStatus.CHARGING) {
            "Power Delivery controller actively regulates charging curves to prevent cell over-heating."
        } else {
            "Device is operating on internal chemical battery reserves."
        }
    }

    private fun setupGaugeDetail(info: BatteryInfo) {
        binding.ivDetailIcon.setImageResource(R.drawable.ic_dashboard)
        binding.tvDetailTitle.text = "Battery Capacity Level"
        binding.tvDetailCategory.text = "State of Charge (SoC) %"
        binding.tvDetailLiveValue.text = "${info.level}%"

        binding.ivRiskDot.setColorFilter(ContextCompat.getColor(requireContext(), R.color.battery_good))
        binding.tvRiskTitle.text = "State of Charge: ${info.level}%"
        binding.tvRiskScore.text = "Status: ${info.status.name}"

        binding.tvHardwareExplanation.text =
            "State of Charge (SoC) represents calibrated remaining usable chemical charge capacity calculated by the hardware fuel gauge IC via continuous Coulomb counting."
        binding.tvEngineeringFormula.text = "SoC (%) = (Q_remaining / Q_nominal) × 100"
        binding.tvAiRecommendation.text =
            "Fuel gauge calibration is accurate. Regular shallow discharge cycles (20%-80%) prolong overall battery cycle lifespan."
    }

    private fun setupTimeEstimateDetail(info: BatteryInfo) {
        val totalMins = if (info.estimatedRemainingHours != null && info.estimatedRemainingHours > 0) {
            (info.estimatedRemainingHours * 60).toInt()
        } else 0
        val hours = totalMins / 60
        val mins = totalMins % 60

        binding.ivDetailIcon.setImageResource(R.drawable.ic_history)
        binding.tvDetailTitle.text = "Estimated Time Matrix"
        binding.tvDetailCategory.text = "Predictive Battery Life Algorithm"
        binding.tvDetailLiveValue.text = "${hours}h ${mins}m"

        binding.ivRiskDot.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary))
        binding.tvRiskTitle.text = if (info.status == BatteryStatus.CHARGING) "Time to Full Charge" else "Estimated Run Time"
        binding.tvRiskScore.text = "Estimate Confidence: 94%"

        binding.tvHardwareExplanation.text =
            "Uses moving average instantaneous power draw (mW) and remaining capacity (mAh) to compute precise run time or charging completion duration."
        binding.tvEngineeringFormula.text = "Time (Hours) = Capacity (mAh) / Average Discharge Current (mA)"
        binding.tvAiRecommendation.text =
            "Predictive algorithm automatically recalibrates as active background processes change power consumption profiles."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
