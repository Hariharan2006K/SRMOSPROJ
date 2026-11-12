package com.voltwise.powermonitor.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.voltwise.powermonitor.R
import com.voltwise.powermonitor.databinding.ActivityMainBinding
import com.voltwise.powermonitor.service.BatteryMonitorService
import com.voltwise.powermonitor.ui.apps.AppsFragment
import com.voltwise.powermonitor.ui.dashboard.DashboardFragment
import com.voltwise.powermonitor.ui.diagnostics.DiagnosticsFragment
import com.voltwise.powermonitor.ui.history.HistoryFragment
import com.voltwise.powermonitor.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestInitialPermissions()
        setupNavigation()
        setupServiceToggleButton()

        // Default to DashboardFragment if first launch
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }
    }

    private fun requestInitialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.menu_dashboard -> DashboardFragment()
                R.id.menu_apps -> AppsFragment()
                R.id.menu_history -> HistoryFragment()
                R.id.menu_diagnostics -> DiagnosticsFragment()
                R.id.menu_settings -> SettingsFragment()
                else -> DashboardFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun setupServiceToggleButton() {
        updateServiceButtonState()

        binding.btnServiceToggle.setOnClickListener {
            if (BatteryMonitorService.isRunning) {
                BatteryMonitorService.stopService(this)
            } else {
                BatteryMonitorService.startService(this)
            }
            binding.root.postDelayed({
                updateServiceButtonState()
            }, 300)
        }
    }

    private fun updateServiceButtonState() {
        if (BatteryMonitorService.isRunning) {
            binding.btnServiceToggle.text = getString(R.string.btn_stop_monitor)
            binding.btnServiceToggle.setIconResource(R.drawable.ic_pause)
            binding.btnServiceToggle.setTextColor(ContextCompat.getColor(this, R.color.battery_critical))
        } else {
            binding.btnServiceToggle.text = getString(R.string.btn_start_monitor)
            binding.btnServiceToggle.setIconResource(R.drawable.ic_play)
            binding.btnServiceToggle.setTextColor(ContextCompat.getColor(this, R.color.primary))
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceButtonState()
    }
}
