package com.voltwise.powermonitor.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voltwise.powermonitor.data.entity.MonitoringSessionEntity
import com.voltwise.powermonitor.databinding.ItemSessionBinding
import com.voltwise.powermonitor.util.FormatUtils

class SessionAdapter : ListAdapter<MonitoringSessionEntity, SessionAdapter.SessionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SessionViewHolder(private val binding: ItemSessionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(session: MonitoringSessionEntity) {
            val startStr = FormatUtils.formatDateTime(session.startTime)
            val endStr = FormatUtils.formatTime(session.endTime)
            binding.tvSessionDate.text = "$startStr - $endStr"

            val durationMs = session.endTime - session.startTime
            binding.tvSessionDuration.text = FormatUtils.formatDuration(durationMs.coerceAtLeast(1000L))

            binding.tvSessionLevelDrop.text = "${session.startLevel}% → ${session.endLevel}% (-${session.levelDrop}%)"
            binding.tvSessionPeakTemp.text = "${String.format("%.1f", session.peakTemperature)} °C"
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MonitoringSessionEntity>() {
        override fun areItemsTheSame(oldItem: MonitoringSessionEntity, newItem: MonitoringSessionEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MonitoringSessionEntity, newItem: MonitoringSessionEntity): Boolean {
            return oldItem == newItem
        }
    }
}
