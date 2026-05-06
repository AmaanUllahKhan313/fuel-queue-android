package com.fuelqueue.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fuelqueue.data.api.RetrofitClient
import com.fuelqueue.data.model.Station
import com.fuelqueue.databinding.FragmentStationListBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StationListFragment : Fragment() {

    private var _binding: FragmentStationListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StationAdapter
    private var refreshJob: Job? = null
    private var loadJob: Job? = null

    private val defaultLat = 18.6298
    private val defaultLng = 73.7997

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StationAdapter { station ->
            val bundle = Bundle().apply { putLong("stationId", station.stationId) }
            findNavController().navigate(com.fuelqueue.R.id.action_list_to_detail, bundle)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadStations() }

        loadStations()
    }

    override fun onResume() {
        super.onResume()
        // Auto-refresh every 25 seconds to keep crowd data current
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(25_000)
                loadStations()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        refreshJob?.cancel()
        loadJob?.cancel()
    }

    private fun loadStations() {
        binding.progressBar.visibility = View.VISIBLE

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getNearbyStations(defaultLat, defaultLng, 15000.0)
                if (response.isSuccessful) {
                    val baseStations = response.body() ?: emptyList()
                    val liveStations = enrichWithLiveCrowd(baseStations)
                    // Submit fresh copies so RecyclerView always rebinds changed fields.
                    adapter.submitList(liveStations.map { it.copy() })
                    binding.tvEmpty.visibility = if (liveStations.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    Toast.makeText(requireContext(), "Failed to load stations", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility   = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private suspend fun enrichWithLiveCrowd(stations: List<Station>): List<Station> = coroutineScope {
        stations.map { station ->
            async {
                try {
                    val crowdResponse = RetrofitClient.api.getCrowdStatus(station.stationId)
                    val crowd = crowdResponse.body()
                    if (crowdResponse.isSuccessful && crowd != null) {
                        station.copy(
                            activeUsers = crowd.activeUsers,
                            estimatedWaitMinutes = crowd.estimatedWaitMinutes,
                            crowdLevel = crowd.crowdLevel,
                            updatedAt = crowd.updatedAt
                        )
                    } else {
                        station
                    }
                } catch (_: Exception) {
                    station
                }
            }
        }.awaitAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
