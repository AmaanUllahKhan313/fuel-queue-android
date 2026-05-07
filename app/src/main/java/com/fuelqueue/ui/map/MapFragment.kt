package com.fuelqueue.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fuelqueue.R
import com.fuelqueue.data.api.RetrofitClient
import com.fuelqueue.data.model.Station
import com.fuelqueue.databinding.FragmentMapBinding
import com.fuelqueue.utils.CrowdUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var googleMap: GoogleMap? = null
    private var refreshJob: Job? = null
    private var loadJob: Job? = null
    private val stationMarkers = mutableMapOf<Long, Marker>()
    private lateinit var fusedClient: FusedLocationProviderClient
    private var hasCenteredOnUser = false
    private var warnedFallback = false
    private var latestUserLocation: LatLng? = null
    private var locationCallback: LocationCallback? = null
    private var lastNearbyRefreshAt = 0L

    // Default center: Pimpri-Chinchwad, Pune
    private val defaultLat = 18.6298
    private val defaultLng = 73.7997

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            enableMyLocationLayer()
            startMapLocationUpdates()
            loadStations(forceRecenter = true)
        } else {
            Toast.makeText(
                requireContext(),
                "Location permission is required to show nearby stations.",
                Toast.LENGTH_LONG
            ).show()
            loadStations(forceRecenter = false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.fabRefresh.setOnClickListener { loadStations(forceRecenter = true) }

        binding.btnViewList.setOnClickListener {
            findNavController().navigate(R.id.action_map_to_list)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        Log.d("MapFragment", "Map is ready, permission status: ${hasLocationPermission()}")

        // Enable my location if permission granted
        if (hasLocationPermission()) {
            Log.d("MapFragment", "Enabling my location layer and starting location updates")
            enableMyLocationLayer()
            startMapLocationUpdates()
        } else {
            Log.w("MapFragment", "Location permission not granted, requesting...")
            requestLocationPermission()
        }

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled      = true

        // Initial fallback camera until user location resolves.
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

        loadStations(forceRecenter = true)
    }

    private fun loadStations(forceRecenter: Boolean = false) {
        binding.progressMap.visibility = View.VISIBLE

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val userLocation = resolveUserLocation()
                val query = userLocation ?: LatLng(defaultLat, defaultLng)

                if (userLocation == null && !warnedFallback) {
                    warnedFallback = true
                    Toast.makeText(
                        requireContext(),
                        "Using fallback location. Enable GPS to see stations near you.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                if (userLocation != null && (!hasCenteredOnUser || forceRecenter)) {
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(userLocation, 14f)
                    )
                    hasCenteredOnUser = true
                }

                val response = RetrofitClient.api.getNearbyStations(query.latitude, query.longitude, 15000.0)
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
            val normalizedCrowd = station.crowdLevel.uppercase(Locale.US)
            val hue = when (normalizedCrowd) {
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
        if (hasLocationPermission()) {
            enableMyLocationLayer()
            startMapLocationUpdates()
        }
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
        loadJob?.cancel()
        stopMapLocationUpdates()
    }

    private suspend fun resolveUserLocation(): LatLng? {
        latestUserLocation?.let { return it }
        if (!hasLocationPermission()) return null

        return suspendCancellableCoroutine { continuation ->
            try {
                val tokenSource = CancellationTokenSource()
                continuation.invokeOnCancellation { tokenSource.cancel() }

                // Try getCurrentLocation first with HIGH_ACCURACY for immediate result
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                    .addOnSuccessListener { currentLocation ->
                        if (continuation.isActive) {
                            if (currentLocation != null) {
                                val latLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                                latestUserLocation = latLng
                                continuation.resume(latLng)
                            } else {
                                // Fallback to lastLocation if getCurrentLocation returns null
                                fusedClient.lastLocation
                                    .addOnSuccessListener { lastLocation ->
                                        if (continuation.isActive) {
                                            val latLng = lastLocation?.let { LatLng(it.latitude, it.longitude) }
                                            if (latLng != null) {
                                                latestUserLocation = latLng
                                            }
                                            continuation.resume(latLng)
                                        }
                                    }
                                    .addOnFailureListener {
                                        if (continuation.isActive) continuation.resume(null)
                                    }
                            }
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
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
        locationPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationLayer() {
        if (!hasLocationPermission()) return
        googleMap?.isMyLocationEnabled = true
    }

    @SuppressLint("MissingPermission")
    private fun startMapLocationUpdates() {
        if (!hasLocationPermission()) return
        if (locationCallback != null) {
            Log.d("MapFragment", "Location updates already running")
            return
        }
        
        Log.d("MapFragment", "Starting location updates with HIGH_ACCURACY")

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMaxUpdateDelayMillis(10_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val newLocation = LatLng(location.latitude, location.longitude)
                Log.d("MapFragment", "Received location: ${newLocation.latitude}, ${newLocation.longitude}, accuracy: ${location.accuracy}m")
                
                val oldLocation = latestUserLocation
                latestUserLocation = newLocation

                // Ensure my location layer is enabled once we have location data
                if (!hasLocationPermission()) return@onLocationResult
                if (googleMap?.isMyLocationEnabled != true) {
                    Log.d("MapFragment", "Re-enabling my location layer now that we have location data")
                    googleMap?.isMyLocationEnabled = true
                }

                if (!hasCenteredOnUser) {
                    Log.d("MapFragment", "Centering camera on user location")
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 14f))
                    hasCenteredOnUser = true
                }

                val shouldRefreshByDistance = oldLocation == null || movedMoreThanMeters(oldLocation, newLocation, 200f)
                val now = System.currentTimeMillis()
                if (shouldRefreshByDistance || now - lastNearbyRefreshAt >= 15_000L) {
                    lastNearbyRefreshAt = now
                    loadStations()
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            Log.d("MapFragment", "Location update request sent successfully")
        } catch (e: SecurityException) {
            Log.e("MapFragment", "Security exception requesting location: ${e.message}")
        }
    }

    private fun stopMapLocationUpdates() {
        val callback = locationCallback ?: return
        fusedClient.removeLocationUpdates(callback)
        locationCallback = null
    }

    private fun movedMoreThanMeters(from: LatLng, to: LatLng, thresholdMeters: Float): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0] >= thresholdMeters
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
