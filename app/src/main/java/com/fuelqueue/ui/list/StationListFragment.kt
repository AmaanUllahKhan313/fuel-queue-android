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
import com.fuelqueue.databinding.FragmentStationListBinding
import kotlinx.coroutines.launch

class StationListFragment : Fragment() {

    private var _binding: FragmentStationListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StationAdapter

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

    private fun loadStations() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getNearbyStations(defaultLat, defaultLng, 15000.0)
                if (response.isSuccessful) {
                    val stations = response.body() ?: emptyList()
                    adapter.submitList(stations)
                    binding.tvEmpty.visibility = if (stations.isEmpty()) View.VISIBLE else View.GONE
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
