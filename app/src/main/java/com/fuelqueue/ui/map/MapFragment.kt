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
    private var userLocationMarker: Marker? = null
    private lateinit var fusedClient: FusedLocationProviderClient
    private var hasCenteredOnUser = false
    private var warnedFallback = false
    private var latestUserLocation: LatLng? = null
    private var locationCallback: LocationCallback? = null
    private var lastNearbyRefreshAt = 0L
    private var isLoadingStations = false

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
        
        Log.d("MapFragment", "onViewCreated: Initializing FusedLocationProviderClient")
        fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        Log.d("MapFragment", "onViewCreated: Getting map asynchronously")
        mapFragment.getMapAsync(this)

        binding.fabRefresh.setOnClickListener { 
            Log.d("MapFragment", "onViewCreated: Refresh button clicked")
            loadStations(forceRecenter = true) 
        }

        binding.btnViewList.setOnClickListener {
            Log.d("MapFragment", "onViewCreated: View list button clicked")
            findNavController().navigate(R.id.action_map_to_list)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        Log.d("MapFragment", "onMapReady: Map is ready, permission status: ${hasLocationPermission()}")

        // Enable my location if permission granted
        if (hasLocationPermission()) {
            Log.d("MapFragment", "onMapReady: Enabling my location layer and starting location updates")
            enableMyLocationLayer()
            startMapLocationUpdates()
        } else {
            Log.w("MapFragment", "onMapReady: Location permission not granted, requesting...")
            requestLocationPermission()
        }

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled      = true

        // Initial fallback camera until user location resolves.
        Log.d("MapFragment", "onMapReady: Setting initial camera to fallback location: $defaultLat, $defaultLng")
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(defaultLat, defaultLng), 13f
        ))

        // Tap marker → open detail
        map.setOnMarkerClickListener { marker ->
            val stationId = marker.tag as? Long ?: return@setOnMarkerClickListener false
            Log.d("MapFragment", "onMapReady: Marker clicked for station $stationId")
            val action = MapFragmentDirections.actionMapToDetail(stationId)
            findNavController().navigate(action)
            true
        }

        Log.d("MapFragment", "onMapReady: Loading initial stations")
        loadStations(forceRecenter = true)
    }

    private fun loadStations(forceRecenter: Boolean = false) {
        // Prevent concurrent loads
        if (isLoadingStations) {
            Log.d("MapFragment", "loadStations: Already loading, skipping")
            return
        }
        isLoadingStations = true
        
        Log.d("MapFragment", "loadStations: Starting (forceRecenter=$forceRecenter)")
        binding.progressMap.visibility = View.VISIBLE

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val userLocation = resolveUserLocation()
                val query = userLocation ?: LatLng(defaultLat, defaultLng)
                
                if (userLocation != null) {
                    Log.d("MapFragment", "loadStations: Using user location: ${userLocation.latitude}, ${userLocation.longitude}")
                } else {
                    Log.d("MapFragment", "loadStations: Using fallback location: ${query.latitude}, ${query.longitude}")
                }

                if (userLocation == null && !warnedFallback) {
                    warnedFallback = true
                    Toast.makeText(
                        requireContext(),
                        "Using fallback location. Enable GPS to see stations near you.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                if (userLocation != null && (!hasCenteredOnUser || forceRecenter)) {
                    Log.d("MapFragment", "loadStations: Animating camera to user location")
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(userLocation, 14f)
                    )
                    hasCenteredOnUser = true
                }

                Log.d("MapFragment", "loadStations: Fetching nearby stations at ${query.latitude}, ${query.longitude}")
                val response = RetrofitClient.api.getNearbyStations(query.latitude, query.longitude, 15000.0)
                if (response.isSuccessful) {
                    val stations = response.body()
                    Log.d("MapFragment", "loadStations: Got ${stations?.size ?: 0} stations")
                    stations?.let { updateMarkers(it) }
                } else {
                    Log.e("MapFragment", "loadStations: API error ${response.code()}")
                    Toast.makeText(requireContext(), "Failed to load stations", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MapFragment", "loadStations: Exception: ${e.message}", e)
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressMap.visibility = View.GONE
                isLoadingStations = false
            }
        }
    }

    private fun updateMarkers(stations: List<Station>) {
        val map = googleMap ?: return

        // Remove old station markers
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

        // Update user location marker
        latestUserLocation?.let { userLoc ->
            userLocationMarker?.remove()
            userLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(userLoc)
                    .title("Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
            Log.d("MapFragment", "updateMarkers: Added user location marker at $userLoc")
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) {
            enableMyLocationLayer()
            startMapLocationUpdates()
        }
        // Auto-refresh every 40 seconds (reduced from 30s to avoid ANR)
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
        stopMapLocationUpdates()
    }

    private suspend fun resolveUserLocation(): LatLng? {
        latestUserLocation?.let { return it }
        if (!hasLocationPermission()) return null

        Log.d("MapFragment", "resolveUserLocation: Starting location resolution")

        // Use longer timeout for initial location request - location providers need time for first fix
        return withTimeoutOrNull(10000L) {
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                try {
                    val tokenSource = CancellationTokenSource()
                    continuation.invokeOnCancellation {
                        Log.d("MapFragment", "resolveUserLocation: Cancellation requested")
                        tokenSource.cancel()
                    }

                    Log.d("MapFragment", "resolveUserLocation: Calling getCurrentLocation")

                    // Try getCurrentLocation first with HIGH_ACCURACY for immediate result
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                        .addOnSuccessListener { currentLocation ->
                            Log.d("MapFragment", "resolveUserLocation: getCurrentLocation callback, location=$currentLocation")
                            if (currentLocation != null && !resumed) {
                                resumed = true
                                val latLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                                Log.d("MapFragment", "resolveUserLocation: Got current location: ${latLng.latitude}, ${latLng.longitude}")
                                latestUserLocation = latLng
                                if (continuation.isActive) continuation.resume(latLng)
                            } else if (currentLocation == null && !resumed) {
                                Log.d("MapFragment", "resolveUserLocation: getCurrentLocation returned null, trying lastLocation")
                                // Fallback to lastLocation if getCurrentLocation returns null
                                fusedClient.lastLocation
                                    .addOnSuccessListener { lastLocation ->
                                        Log.d("MapFragment", "resolveUserLocation: lastLocation callback, location=$lastLocation")
                                        if (!resumed) {
                                            resumed = true
                                            val latLng = lastLocation?.let {
                                                Log.d("MapFragment", "resolveUserLocation: Got last location: ${it.latitude}, ${it.longitude}")
                                                LatLng(it.latitude, it.longitude)
                                            }
                                            if (latLng != null) {
                                                latestUserLocation = latLng
                                            }
                                            if (continuation.isActive) continuation.resume(latLng)
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.d("MapFragment", "resolveUserLocation: lastLocation failed: ${e.message}")
                                        if (!resumed) {
                                            resumed = true
                                            if (continuation.isActive) continuation.resume(null)
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.d("MapFragment", "resolveUserLocation: getCurrentLocation failed: ${e.message}")
                            if (!resumed) {
                                resumed = true
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }
                } catch (e: SecurityException) {
                    Log.e("MapFragment", "resolveUserLocation: SecurityException: ${e.message}")
                    if (!resumed) {
                        resumed = true
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }.also { result ->
            if (result == null) {
                Log.w("MapFragment", "resolveUserLocation: Timed out or failed, returning null")
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
        if (!hasLocationPermission()) {
            Log.w("MapFragment", "startMapLocationUpdates: No location permission")
            return
        }
        if (locationCallback != null) {
            Log.d("MapFragment", "startMapLocationUpdates: Location updates already running")
            return
        }
        
        Log.d("MapFragment", "startMapLocationUpdates: Starting location updates with HIGH_ACCURACY")

        // Request frequent updates to capture location quickly
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMaxUpdateDelayMillis(5_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val newLocation = LatLng(location.latitude, location.longitude)
                Log.d("MapFragment", "onLocationResult: Got location ${newLocation.latitude}, ${newLocation.longitude}, accuracy: ${location.accuracy}m")

                val oldLocation = latestUserLocation
                latestUserLocation = newLocation

                // Ensure my location layer is enabled once we have location data
                if (!hasLocationPermission()) return@onLocationResult
                if (googleMap?.isMyLocationEnabled != true) {
                    Log.d("MapFragment", "onLocationResult: Re-enabling my location layer")
                    googleMap?.isMyLocationEnabled = true
                }

                // Center camera on first location or if moved significantly
                if (!hasCenteredOnUser) {
                    Log.d("MapFragment", "onLocationResult: Centering camera on user location (first time)")
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 14f))
                    hasCenteredOnUser = true
                }

                // Trigger station reload if location changed significantly
                val shouldRefreshByDistance = oldLocation == null || movedMoreThanMeters(oldLocation, newLocation, 200f)
                val now = System.currentTimeMillis()
                if (shouldRefreshByDistance || now - lastNearbyRefreshAt >= 15_000L) {
                    Log.d("MapFragment", "onLocationResult: Triggering station reload (distance=$shouldRefreshByDistance, time=${now - lastNearbyRefreshAt}ms)")
                    lastNearbyRefreshAt = now
                    loadStations()
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            Log.d("MapFragment", "startMapLocationUpdates: Location update request sent successfully")
        } catch (e: SecurityException) {
            Log.e("MapFragment", "startMapLocationUpdates: Security exception: ${e.message}")
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
