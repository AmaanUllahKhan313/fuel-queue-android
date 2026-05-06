package com.fuelqueue.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuelqueue.data.model.Station
import com.fuelqueue.databinding.ItemStationBinding
import com.fuelqueue.utils.CrowdUtils

class StationAdapter(
    private val onItemClick: (Station) -> Unit
) : ListAdapter<Station, StationAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemStationBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(station: Station) {
            binding.tvStationName.text    = station.name
            binding.tvAddress.text        = station.address
            binding.tvCrowdLevel.text     = CrowdUtils.getLabel(station.crowdLevel)
            binding.tvCrowdLevel.setTextColor(CrowdUtils.getColor(station.crowdLevel))
            binding.tvActiveUsers.text    = "${station.activeUsers}"
            binding.tvWaitTime.text       = "${station.estimatedWaitMinutes}m"
            binding.tvDistance.text       = if (station.distanceMeters < 1000)
                "${station.distanceMeters.toInt()} m"
            else
                "${"%.1f".format(station.distanceMeters / 1000)} km"
            binding.tvCrowdEmoji.text     = CrowdUtils.getEmoji(station.crowdLevel)

            // Crowd indicator bar color
            binding.crowdIndicator.setBackgroundColor(CrowdUtils.getColor(station.crowdLevel))

            binding.root.setOnClickListener { onItemClick(station) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Station>() {
        override fun areItemsTheSame(a: Station, b: Station) = a.stationId == b.stationId
        override fun areContentsTheSame(a: Station, b: Station) = a == b
    }
}
