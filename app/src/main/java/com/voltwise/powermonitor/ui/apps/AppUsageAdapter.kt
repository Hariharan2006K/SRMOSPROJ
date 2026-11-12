package com.voltwise.powermonitor.ui.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voltwise.powermonitor.R
import com.voltwise.powermonitor.databinding.ItemAppUsageBinding
import com.voltwise.powermonitor.usage.AppUsageInfo

class AppUsageAdapter(
    private val onAppClick: (AppUsageInfo) -> Unit
) : ListAdapter<AppUsageInfo, AppUsageAdapter.AppViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding, onAppClick)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(
        private val binding: ItemAppUsageBinding,
        private val onAppClick: (AppUsageInfo) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppUsageInfo) {
            val context = binding.root.context

            binding.tvAppName.text = item.appName
            binding.ivAppIcon.setImageDrawable(item.icon ?: ContextCompat.getDrawable(context, R.drawable.ic_apps))
            binding.tvStandbyBucket.text = item.standbyBucket
            binding.tvForegroundDuration.text = "Active: ${item.formattedDuration}"

            binding.tvPowerMah.text = "${item.estimatedPowerMah} mAh"
            binding.tvPowerPercentage.text = "${item.percentageOfTotal}%"

            // Set progress bar (percentage of total discharge)
            val progressVal = item.percentageOfTotal.toInt().coerceIn(1, 100)
            binding.progressBatteryImpact.progress = progressVal

            // Color-code Standby Bucket
            val bucketColor = when (item.standbyBucket) {
                "ACTIVE" -> ContextCompat.getColor(context, R.color.secondary)
                "WORKING_SET" -> ContextCompat.getColor(context, R.color.battery_good)
                "FREQUENT" -> ContextCompat.getColor(context, R.color.battery_moderate)
                "RARE", "RESTRICTED" -> ContextCompat.getColor(context, R.color.battery_critical)
                else -> ContextCompat.getColor(context, R.color.text_secondary_dark)
            }
            binding.tvStandbyBucket.setTextColor(bucketColor)

            binding.root.setOnClickListener {
                onAppClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AppUsageInfo>() {
        override fun areItemsTheSame(oldItem: AppUsageInfo, newItem: AppUsageInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppUsageInfo, newItem: AppUsageInfo): Boolean {
            return oldItem == newItem
        }
    }
}
