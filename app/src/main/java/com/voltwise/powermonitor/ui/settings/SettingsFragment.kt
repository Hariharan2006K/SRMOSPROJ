package com.voltwise.powermonitor.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.voltwise.powermonitor.R
import com.voltwise.powermonitor.databinding.FragmentSettingsBinding
import com.voltwise.powermonitor.usage.AppUsageManager
import com.voltwise.powermonitor.util.PreferenceManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var usageManager: AppUsageManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(requireContext(), "Notification permission granted", Toast.LENGTH_SHORT).show()
        }
        updatePermissionButtons()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager(requireContext())
        usageManager = AppUsageManager(requireContext())

        setupPreferencesUI()
        setupListeners()
    }

    private fun setupPreferencesUI() {
        // Interval chips
        when (preferenceManager.samplingIntervalMs) {
            60_000L -> binding.chipInterval1m.isChecked = true
            300_000L -> binding.chipInterval5m.isChecked = true
            900_000L -> binding.chipInterval15m.isChecked = true
            else -> binding.chipInterval1m.isChecked = true
        }

        binding.switchAutoBoot.isChecked = preferenceManager.isAutoStartOnBootEnabled
    }

    private fun setupListeners() {
        // Usage Access button
        binding.btnSettingUsageAccess.setOnClickListener {
            startActivity(usageManager.getUsageAccessIntent())
        }

        // Notification Permission button
        binding.btnSettingNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(requireContext(), "Notification permission is granted by default on your Android version.", Toast.LENGTH_SHORT).show()
            }
        }

        // Battery Optimization button
        binding.btnSettingBatteryOpt.setOnClickListener {
            startActivity(usageManager.getBatteryOptimizationIntent())
        }

        // Interval chip group
        binding.chipGroupInterval.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipInterval1m) -> preferenceManager.samplingIntervalMs = 60_000L
                checkedIds.contains(R.id.chipInterval5m) -> preferenceManager.samplingIntervalMs = 300_000L
                checkedIds.contains(R.id.chipInterval15m) -> preferenceManager.samplingIntervalMs = 900_000L
            }
            Toast.makeText(requireContext(), "Sampling interval updated", Toast.LENGTH_SHORT).show()
        }

        // Auto boot switch
        binding.switchAutoBoot.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.isAutoStartOnBootEnabled = isChecked
        }
    }

    private fun updatePermissionButtons() {
        // Usage permission
        val hasUsage = usageManager.hasUsagePermission()
        if (hasUsage) {
            binding.btnSettingUsageAccess.text = getString(R.string.granted)
            binding.btnSettingUsageAccess.isEnabled = false
            binding.btnSettingUsageAccess.setTextColor(ContextCompat.getColor(requireContext(), R.color.battery_good))
        } else {
            binding.btnSettingUsageAccess.text = getString(R.string.btn_grant)
            binding.btnSettingUsageAccess.isEnabled = true
        }

        // Notification permission
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (hasNotif) {
            binding.btnSettingNotification.text = getString(R.string.granted)
            binding.btnSettingNotification.isEnabled = false
            binding.btnSettingNotification.setTextColor(ContextCompat.getColor(requireContext(), R.color.battery_good))
        } else {
            binding.btnSettingNotification.text = getString(R.string.btn_grant)
            binding.btnSettingNotification.isEnabled = true
        }

        // Battery Optimization ignore
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(requireContext().packageName) ?: false
        } else {
            true
        }
        if (isIgnoring) {
            binding.btnSettingBatteryOpt.text = "Exempted"
            binding.btnSettingBatteryOpt.setTextColor(ContextCompat.getColor(requireContext(), R.color.battery_good))
        } else {
            binding.btnSettingBatteryOpt.text = "Configure"
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
