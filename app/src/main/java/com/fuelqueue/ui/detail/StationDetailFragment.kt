package com.fuelqueue.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fuelqueue.data.api.RetrofitClient
import com.fuelqueue.data.model.CrowdStatus
import com.fuelqueue.databinding.FragmentStationDetailBinding
import com.fuelqueue.utils.CrowdUtils
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class StationDetailFragment : Fragment() {

    private var _binding: FragmentStationDetailBinding? = null
    private val binding get() = _binding!!
    private var refreshJob: Job? = null
    private var stationId: Long = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stationId = arguments?.getLong("stationId") ?: -1
        if (stationId == -1L) {
            Toast.makeText(requireContext(), "Invalid station", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRefresh.setOnClickListener { loadCrowdStatus() }
        loadCrowdStatus()
    }

    override fun onResume() {
        super.onResume()
        // Auto-refresh every 15 seconds
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(15_000)
                loadCrowdStatus()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        refreshJob?.cancel()
    }

    private fun loadCrowdStatus() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getCrowdStatus(stationId)
                if (response.isSuccessful && response.body() != null) {
                    updateUI(response.body()!!)
                } else {
                    Toast.makeText(requireContext(), "Failed to load crowd data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateUI(status: CrowdStatus) {
        binding.tvStationName.text    = status.stationName
        binding.tvCrowdLabel.text     = CrowdUtils.getLabel(status.crowdLevel)
        binding.tvCrowdLabel.setTextColor(CrowdUtils.getColor(status.crowdLevel))
        binding.tvCrowdEmoji.text     = CrowdUtils.getEmoji(status.crowdLevel)
        binding.tvActiveUsers.text    = "${status.activeUsers} vehicles currently here"
        binding.tvWaitTime.text       = "Estimated wait: ~${status.estimatedWaitMinutes} minutes"
        binding.tvAdvice.text         = CrowdUtils.getAdvice(status.crowdLevel)

        val sdf  = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        binding.tvLastUpdated.text = "Last updated: ${sdf.format(Date(status.updatedAt))}"

        // Color the background card
        binding.cardCrowdStatus.setCardBackgroundColor(
            CrowdUtils.getColor(status.crowdLevel)
        )

        // Progress bar (0–10 vehicles max)
        val progress = minOf(status.activeUsers * 10, 100)
        binding.crowdProgressBar.progress = progress
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
