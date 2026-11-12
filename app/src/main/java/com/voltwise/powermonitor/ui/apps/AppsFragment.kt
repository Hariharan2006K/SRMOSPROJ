package com.voltwise.powermonitor.ui.apps

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.voltwise.powermonitor.R
import com.voltwise.powermonitor.databinding.FragmentAppsBinding
import com.voltwise.powermonitor.usage.AppUsageInfo
import com.voltwise.powermonitor.usage.AppUsageManager
import kotlinx.coroutines.launch

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppsViewModel by viewModels()
    private lateinit var adapter: AppUsageAdapter
    private lateinit var usageManager: AppUsageManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        usageManager = AppUsageManager(requireContext())

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = AppUsageAdapter { app ->
            showAppDetailsDialog(app)
        }
        binding.recyclerApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerApps.adapter = adapter
    }

    private fun setupListeners() {
        // Swipe to Refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData()
        }

        // Time Range Chips
        binding.chipGroupTime.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chip1h) -> viewModel.setTimeRange(AppsViewModel.TimeRange.HOUR_1)
                checkedIds.contains(R.id.chip6h) -> viewModel.setTimeRange(AppsViewModel.TimeRange.HOURS_6)
                checkedIds.contains(R.id.chip24h) -> viewModel.setTimeRange(AppsViewModel.TimeRange.HOURS_24)
                checkedIds.contains(R.id.chip7d) -> viewModel.setTimeRange(AppsViewModel.TimeRange.DAYS_7)
            }
        }

        // User Apps Only Switch
        binding.switchUserAppsOnly.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleUserAppsOnly(isChecked)
        }

        // Sort Button
        binding.btnSort.setOnClickListener {
            val nextSort = when (viewModel.selectedSort) {
                AppsViewModel.SortCriteria.POWER -> AppsViewModel.SortCriteria.DURATION
                AppsViewModel.SortCriteria.DURATION -> AppsViewModel.SortCriteria.NAME
                AppsViewModel.SortCriteria.NAME -> AppsViewModel.SortCriteria.POWER
            }
            viewModel.setSortCriteria(nextSort)
            binding.btnSort.text = when (nextSort) {
                AppsViewModel.SortCriteria.POWER -> getString(R.string.sort_power)
                AppsViewModel.SortCriteria.DURATION -> getString(R.string.sort_time)
                AppsViewModel.SortCriteria.NAME -> getString(R.string.sort_name)
            }
        }

        // Grant Permission Button
        binding.btnGrantPermission.setOnClickListener {
            startActivity(usageManager.getUsageAccessIntent())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.hasUsagePermission.collect { hasPerm ->
                        binding.cardPermissionWarning.visibility = if (hasPerm) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.appsList.collect { apps ->
                        adapter.submitList(apps)
                        binding.tvEmptyApps.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
                        binding.tvAppCountBadge.text = "${apps.size} Active Apps"
                    }
                }
                launch {
                    viewModel.totalEnergyDrained.collect { totalMah ->
                        val nominalPercent = ((totalMah / 4500f) * 100f).toInt()
                        binding.tvTotalEnergyDrained.text = "${String.format("%.1f", totalMah)} mAh (~${nominalPercent}%)"
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.swipeRefresh.isRefreshing = loading
                    }
                }
            }
        }
    }

    private fun showAppDetailsDialog(app: AppUsageInfo) {
        val message = """
            Package: ${app.packageName}
            Foreground Duration: ${app.formattedDuration}
            Estimated Energy: ${app.estimatedPowerMah} mAh (${app.percentageOfTotal}% of total discharge)
            Android Standby Bucket: ${app.standbyBucket}
            Category: ${if (app.isSystemApp) "System Framework / OEM" else "User Application"}
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(app.appName)
            .setIcon(app.icon)
            .setMessage(message)
            .setPositiveButton("App Info & Battery") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
        viewModel.loadData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
