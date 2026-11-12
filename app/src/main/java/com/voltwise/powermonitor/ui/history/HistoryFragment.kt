package com.voltwise.powermonitor.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.voltwise.powermonitor.databinding.FragmentHistoryBinding
import com.voltwise.powermonitor.util.FormatUtils
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var sessionAdapter: SessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionAdapter = SessionAdapter()
        binding.recyclerSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSessions.adapter = sessionAdapter

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        // Chart Scrubber Callback
        binding.trendChartView.onPointSelected = { point ->
            if (point != null) {
                val timeStr = FormatUtils.formatTime(point.timestamp)
                binding.tvScrubberDetail.text = "At $timeStr: ${point.level}% • ${String.format("%.0f", point.powerMw)} mW • ${String.format("%.1f", point.temperatureCelsius)}°C (${point.status})"
            } else {
                binding.tvScrubberDetail.text = "Touch and drag across chart to inspect point telemetry"
            }
        }

        // Export CSV Button
        binding.btnExportCsv.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val shareIntent = viewModel.exportCsv()
                if (shareIntent != null) {
                    startActivity(Intent.createChooser(shareIntent, "Share Battery & Activity Logs CSV"))
                } else {
                    Toast.makeText(requireContext(), "No logs available to export. Start monitoring first.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Clear History Button
        binding.btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Historical Data")
                .setMessage("Are you sure you want to permanently delete all recorded battery telemetry and app activity snapshots?")
                .setPositiveButton("Clear All") { _, _ ->
                    viewModel.clearAllLogs()
                    Toast.makeText(requireContext(), "Historical logs cleared.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recentSnapshots.collect { snapshots ->
                        // Recent snapshots come DESC from query, reverse to show ASC chronological order on chart
                        val chronological = snapshots.reversed()
                        binding.trendChartView.submitLogs(chronological)
                    }
                }
                launch {
                    viewModel.sessions.collect { sessions ->
                        sessionAdapter.submitList(sessions)
                        binding.tvEmptySessions.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.snapshotCount.collect { count ->
                        binding.tvSnapshotCount.text = "$count records"
                    }
                }
                launch {
                    viewModel.dischargeRateText.collect { rate ->
                        binding.tvDischargeRate.text = rate
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAnalytics()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
