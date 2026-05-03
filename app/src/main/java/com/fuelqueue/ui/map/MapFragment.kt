package com.fuelqueue.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fuelqueue.R
import com.fuelqueue.data.api.RetrofitClient
import com.fuelqueue.data.model.Station
import com.fuelqueue.databinding.FragmentMapBinding
import com.fuelqueue.utils.CrowdUtils
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.*

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var googleMap: GoogleMap? = null
    private var refreshJob: Job? = null
    private val stationMarkers = mutableMapOf<Long, Marker>()

    // Default center: Pimpri-Chinchwad, Pune
    private val defaultLat = 18.6298
    private val defaultLng = 73.7997

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.fabRefresh.setOnClickListener { loadStations() }

        binding.btnViewList.setOnClickListener {
            findNavController().navigate(R.id.action_map_to_list)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Enable my location if permission granted
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
        }

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled      = true

        // Center on Pimpri-Chinchwad
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(defaultLat, defaultLng), 13f
        ))

        // Tap marker → open detail
        map.setOnMarkerClickListener { marker ->
            val stationId = marker.tag as? Long ?: return@setOnMarkerClickListener false
            val action = MapFragmentDirections.actionMapToDetail(stationId)
            findNavController().navigate(action)
            true
        }

        loadStations()
    }

    private fun loadStations() {
        binding.progressMap.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getNearbyStations(defaultLat, defaultLng, 15000.0)
                if (response.isSuccessful) {
                    response.body()?.let { stations ->
                        updateMarkers(stations)
                    }
                } else {
                    Toast.makeText(requireContext(), "Failed to load stations", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressMap.visibility = View.GONE
            }
        }
    }

    private fun updateMarkers(stations: List<Station>) {
        val map = googleMap ?: return

        // Remove old markers
        stationMarkers.values.forEach { it.remove() }
        stationMarkers.clear()

        stations.forEach { station ->
            val hue = when (station.crowdLevel) {
                "LOW"    -> BitmapDescriptorFactory.HUE_GREEN
                "MEDIUM" -> BitmapDescriptorFactory.HUE_YELLOW
                "HIGH"   -> BitmapDescriptorFactory.HUE_RED
                else     -> BitmapDescriptorFactory.HUE_AZURE
            }
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(station.latitude, station.longitude))
                    .title(station.name)
                    .snippet("${CrowdUtils.getEmoji(station.crowdLevel)} ${station.crowdLevel} • ~${station.estimatedWaitMinutes} min wait")
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
            )
            marker?.tag = station.stationId
            if (marker != null) stationMarkers[station.stationId] = marker
        }
    }

    override fun onResume() {
        super.onResume()
        // Auto-refresh every 30 seconds
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(30_000)
                loadStations()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        refreshJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
