package com.fuelqueue.ui.list

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fuelqueue.data.api.RetrofitClient
import com.fuelqueue.data.model.Station
import com.fuelqueue.databinding.FragmentStationListBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class StationListFragment : Fragment() {

    private var _binding: FragmentStationListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StationAdapter
    private lateinit var fusedClient: FusedLocationProviderClient
    private var refreshJob: Job? = null
    private var loadJob: Job? = null
    private var warnedLocationFallback = false
    private var latestUserLocation: QueryLocation? = null
    private var locationCallback: LocationCallback? = null
    private var lastNearbyRefreshAt = 0L
    private var isLoadingStations = false

    private val defaultLat = 18.6298
    private val defaultLng = 73.7997

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            startListLocationUpdates()
            loadStations()
        } else {
            Toast.makeText(
                requireContext(),
                "Location permission is required to show stations near you.",
                Toast.LENGTH_LONG
            ).show()
            loadStations()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        if (hasLocationPermission()) {
            startListLocationUpdates()
        } else {
            requestLocationPermission()
        }

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
        if (hasLocationPermission()) {
            startListLocationUpdates()
        }
        // Auto-refresh every 40 seconds to keep crowd data current (reduced from 25s to avoid ANR)
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(40_000)
                if (!isLoadingStations) {
                    loadStations()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        refreshJob?.cancel()
        loadJob?.cancel()
        stopListLocationUpdates()
    }

    private fun loadStations() {
        // Prevent concurrent loads
        if (isLoadingStations) return
        isLoadingStations = true
        
        binding.progressBar.visibility = View.VISIBLE

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val userLocation = resolveUserLocation()
                val queryLat = userLocation?.latitude ?: defaultLat
                val queryLng = userLocation?.longitude ?: defaultLng

                binding.tvHeaderSubtitle.text = if (userLocation != null) {
                    "Using your current location • within 15 km"
                } else {
                    if (!warnedLocationFallback) {
                        warnedLocationFallback = true
                        Toast.makeText(
                            requireContext(),
                            "Using fallback location. Enable location permission/GPS for stations near you.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    "Location unavailable • showing Pimpri-Chinchwad within 15 km"
                }

                val response = RetrofitClient.api.getNearbyStations(queryLat, queryLng, 15000.0)
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
                isLoadingStations = false
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
                            crowdLevel = crowd.crowdLevel.uppercase(Locale.US),
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

    private suspend fun resolveUserLocation(): QueryLocation? {
        latestUserLocation?.let { return it }
        if (!hasLocationPermission()) return null

        // Use 10-second timeout for location resolution
        return withTimeoutOrNull(10000L) {
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                try {
                    fusedClient.lastLocation
                        .addOnSuccessListener { lastLocation ->
                            if (lastLocation != null && !resumed) {
                                resumed = true
                                if (continuation.isActive) {
                                    continuation.resume(
                                        QueryLocation(lastLocation.latitude, lastLocation.longitude).also {
                                            latestUserLocation = it
                                        }
                                    )
                                }
                            } else if (lastLocation == null && !resumed) {
                                val tokenSource = CancellationTokenSource()
                                continuation.invokeOnCancellation { tokenSource.cancel() }

                                fusedClient.getCurrentLocation(
                                    Priority.PRIORITY_HIGH_ACCURACY,
                                    tokenSource.token
                                )
                                    .addOnSuccessListener { currentLocation ->
                                        if (!resumed) {
                                            resumed = true
                                            if (continuation.isActive) {
                                                continuation.resume(
                                                    currentLocation?.let {
                                                        QueryLocation(it.latitude, it.longitude).also { resolved ->
                                                            latestUserLocation = resolved
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    .addOnFailureListener {
                                        if (!resumed) {
                                            resumed = true
                                            if (continuation.isActive) continuation.resume(null)
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener {
                            if (!resumed) {
                                resumed = true
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }
                } catch (_: SecurityException) {
                    if (!resumed) {
                        resumed = true
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val context = requireContext()
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun startListLocationUpdates() {
        if (!hasLocationPermission()) return
        if (locationCallback != null) return

        // Request frequent updates to capture location quickly
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMaxUpdateDelayMillis(5_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val newLocation = QueryLocation(location.latitude, location.longitude)
                val oldLocation = latestUserLocation
                latestUserLocation = newLocation

                val shouldRefreshByDistance = oldLocation == null || movedMoreThanMeters(oldLocation, newLocation, 200f)
                val now = System.currentTimeMillis()
                if (shouldRefreshByDistance || now - lastNearbyRefreshAt >= 15_000L) {
                    lastNearbyRefreshAt = now
                    loadStations()
                }
            }
        }

        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun stopListLocationUpdates() {
        val callback = locationCallback ?: return
        fusedClient.removeLocationUpdates(callback)
        locationCallback = null
    }

    private fun movedMoreThanMeters(from: QueryLocation, to: QueryLocation, thresholdMeters: Float): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0] >= thresholdMeters
    }

    private data class QueryLocation(
        val latitude: Double,
        val longitude: Double
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
