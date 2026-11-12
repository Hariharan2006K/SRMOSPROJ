package com.voltwise.powermonitor.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.voltwise.powermonitor.R
import com.voltwise.powermonitor.battery.BatteryInfo
import com.voltwise.powermonitor.battery.BatteryStatus
import com.voltwise.powermonitor.databinding.FragmentDashboardBinding
import com.voltwise.powermonitor.util.FormatUtils
import kotlinx.coroutines.launch
import kotlin.math.abs

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardStatusBanner.setOnClickListener {
            viewModel.toggleService()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.batteryInfo.collect { info ->
                        if (info != null) {
                            updateUI(info)
                        }
                    }
                }
                launch {
                    viewModel.isServiceRunning.collect { isRunning ->
                        updateServiceStatusBanner(isRunning)
                    }
                }
            }
        }
    }

    private fun updateUI(info: BatteryInfo) {
        // Update Gauge
        binding.batteryGauge.setBatteryData(info.level, info.status, info.powerMw)

        // Telemetry values
        val currentSign = if (info.currentNowMa > 0) "+" else ""
        binding.tvPowerMw.text = "${String.format("%.0f", info.powerMw)} mW"
        binding.tvCurrentMa.text = "$currentSign${String.format("%.0f", info.currentNowMa)} mA"
        binding.tvVoltage.text = "${String.format("%.2f", info.voltageMv / 1000f)} V"
        binding.tvTemperature.text = "${String.format("%.1f", info.temperatureCelsius)} °C"
        binding.tvHealth.text = info.health.name
        binding.tvPlugSource.text = info.plugType.name

        // Remaining time estimate
        if (info.estimatedRemainingHours != null && info.estimatedRemainingHours > 0f) {
            val totalMinutes = (info.estimatedRemainingHours * 60).toInt()
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            val prefix = if (info.status == BatteryStatus.CHARGING) "Time to Full: " else "Est. Remaining: "
            binding.tvRemainingEstimate.text = "$prefix${hours}h ${mins}m"
        } else {
            binding.tvRemainingEstimate.text = if (info.status == BatteryStatus.CHARGING) "Charging (Calculating…)" else "Active (Calculating…)"
        }

        binding.tvLastSampleTime.text = FormatUtils.formatTime(info.timestamp)
    }

    private fun updateServiceStatusBanner(isRunning: Boolean) {
        if (isRunning) {
            binding.tvStatusBannerText.text = getString(R.string.monitoring_active)
            binding.ivStatusDot.setColorFilter(ContextCompat.getColor(requireContext(), R.color.battery_good))
        } else {
            binding.tvStatusBannerText.text = getString(R.string.monitoring_inactive)
            binding.ivStatusDot.setColorFilter(ContextCompat.getColor(requireContext(), R.color.battery_warning))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshTelemetry()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
