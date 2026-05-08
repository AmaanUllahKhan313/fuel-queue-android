package com.fuelqueue.ui.detail

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fuelqueue.R
import com.fuelqueue.data.api.RetrofitClient
import com.fuelqueue.data.model.CrowdStatus
import com.fuelqueue.databinding.FragmentStationDetailBinding
import com.fuelqueue.utils.CrowdUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import androidx.core.view.isVisible

class StationDetailFragment : Fragment() {

    private var _binding: FragmentStationDetailBinding? = null
    private val binding get() = _binding!!
    private var refreshJob: Job? = null
    private var stationId: Long = -1
    private var stationLatitude: Double = 0.0
    private var stationLongitude: Double = 0.0
    private var stationName: String = ""
    private var isLiveStation: Boolean = false
    private var lastActiveUsers: Int = 0  // Cache for using when live status updates


    private lateinit var fusedClient: FusedLocationProviderClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        stationId = arguments?.getLong("stationId") ?: -1
        if (stationId == -1L) {
            Toast.makeText(requireContext(), "Invalid station", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRefresh.setOnClickListener { loadCrowdStatus(fetchLiveStatus = true) }
        binding.btnGetDirections.setOnClickListener { openDirections() }
        loadCrowdStatus(fetchLiveStatus = true)  // Fetch live status on initial load
    }

    override fun onResume() {
        super.onResume()
        // Auto-refresh every 60 seconds (increased from 15s to prevent ANR)
        // Only refreshes crowd status, live status fetched only on initial load
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(60_000)
                Log.d("StationDetail", "Auto-refresh triggered (60s interval)")
                loadCrowdStatus(fetchLiveStatus = false) // Don't fetch live status on refresh
            }
        }
    }

    override fun onPause() {
        super.onPause()
        refreshJob?.cancel()
    }

    private fun loadCrowdStatus(fetchLiveStatus: Boolean = true) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getCrowdStatus(stationId)
                if (response.isSuccessful && response.body() != null) {
                    updateUI(response.body()!!)
                } else {
                    Toast.makeText(requireContext(), "Failed to load crowd data", Toast.LENGTH_SHORT).show()
                }

                // Only fetch live status on initial load or if explicitly requested
                // Skip on auto-refresh to reduce API call frequency and prevent ANR
                if (fetchLiveStatus) {
                    fetchStationDetailsForLiveStatus()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun fetchStationDetailsForLiveStatus() {
        lifecycleScope.launch {
            try {
                Log.d("StationDetail", "→ Fetching station live status...")
                val response = RetrofitClient.api.getStationById(stationId)
                if (response.isSuccessful && response.body() != null) {
                    val station = response.body()!!
                    Log.d("StationDetail", "← Station received: isLive=${station.isLive}")
                    updateLiveIndicator(station.isLive)
                    
                    // KEY: Update stock indicator immediately with the live status
                    // This ensures green "stock available" shows when isLive=true
                    updateStockAvailableIndicator(lastActiveUsers)
                }
            } catch (e: Exception) {
                Log.d("StationDetail", "Failed to fetch live status: ${e.message}")
            }
        }
    }

    private fun updateLiveIndicator(isLive: Boolean) {
        isLiveStation = isLive
        Log.d("StationDetail", "LIVE STATUS UPDATED: isLiveStation=$isLiveStation")

        if (isLive) {
            binding.cardLiveIndicator.visibility = View.VISIBLE
            Log.d("StationDetail", "✓ LIVE indicator visible")
        } else {
            binding.cardLiveIndicator.visibility = View.GONE
            Log.d("StationDetail", "✗ LIVE indicator hidden")
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
        
        // Cache active users for when live status arrives later
        lastActiveUsers = status.activeUsers

        // Update stock available indicator
        updateStockAvailableIndicator(status.activeUsers)
    }
    
    private fun updateStockAvailableIndicator(activeUsers: Int) {
        // Logic: Stock is available if:
        // 1. Station is LIVE (highest priority - always show available)
        // 2. OR there are active users (vehicles) currently at station
        val isStockAvailable = isLiveStation || activeUsers > 0

        Log.d("StationDetail", "STOCK: isLive=$isLiveStation, activeUsers=$activeUsers → available=$isStockAvailable")

        if (isStockAvailable) {
            // Stock IS available - show GREEN indicator
            binding.tvStockStatusLabel.text = "stock available"
            binding.tvStockStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            binding.tvStockIndicatorEmoji.text = "🟢"
            val greenDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.stock_available_background)
            binding.stockAvailableIndicator.background = greenDrawable
            Log.d("StationDetail", "✓ SHOW: stock available (GREEN)")
        } else {
            // Stock NOT available - show RED indicator
            binding.tvStockStatusLabel.text = "stock not available"
            binding.tvStockStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            binding.tvStockIndicatorEmoji.text = "🔴"
            val redDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.stock_not_available_background)
            binding.stockAvailableIndicator.background = redDrawable
            Log.d("StationDetail", "✗ SHOW: stock not available (RED)")
        }
    }

    private fun openDirections() {
        lifecycleScope.launch {
            try {
                // Get user's current location
                val userLocation = getUserLocation()

                if (userLocation == null) {
                    Toast.makeText(
                        requireContext(),
                        "Could not get your location. Please enable location services and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val userLat = userLocation.first
                val userLng = userLocation.second

                // Fetch full station details including coordinates
                val stationResponse = try {
                    RetrofitClient.api.getStationById(stationId)
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Could not fetch station details: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                if (stationResponse.isSuccessful && stationResponse.body() != null) {
                    val station = stationResponse.body()!!
                    stationLatitude = station.latitude
                    stationLongitude = station.longitude
                    stationName = station.name
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Could not fetch station location. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Check if we have valid station coordinates
                if (stationLatitude == 0.0 || stationLongitude == 0.0) {
                    Toast.makeText(
                        requireContext(),
                        "Station location data not available.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Open Google Maps with directions
                launchGoogleMapsDirections(userLat, userLng, stationLatitude, stationLongitude, stationName)

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getUserLocation(): Pair<Double, Double>? {
        return withTimeoutOrNull(8000L) {
            suspendCancellableCoroutine { continuation ->
                try {
                    // Try lastLocation first
                    fusedClient.lastLocation.addOnSuccessListener { lastLocation ->
                        if (lastLocation != null && continuation.isActive) {
                            continuation.resume(Pair(lastLocation.latitude, lastLocation.longitude))
                        } else {
                            // Try getCurrentLocation if lastLocation is null
                            val tokenSource = CancellationTokenSource()
                            continuation.invokeOnCancellation { tokenSource.cancel() }

                            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                                .addOnSuccessListener { currentLocation ->
                                    if (currentLocation != null && continuation.isActive) {
                                        continuation.resume(Pair(currentLocation.latitude, currentLocation.longitude))
                                    } else if (continuation.isActive) {
                                        continuation.resume(null)
                                    }
                                }
                                .addOnFailureListener {
                                    if (continuation.isActive) continuation.resume(null)
                                }
                        }
                    }.addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private fun launchGoogleMapsDirections(
        userLat: Double,
        userLng: Double,
        destLat: Double,
        destLng: Double,
        @Suppress("UNUSED_PARAMETER") destName: String
    ) {
        try {
            // Build the URL with directions from user location to destination
            // Format: https://www.google.com/maps/dir/?api=1&origin=lat,lng&destination=lat,lng&travelmode=driving
            val mapsUri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1&origin=$userLat,$userLng&destination=$destLat,$destLng&travelmode=driving"
            )

            val intent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
                setPackage("com.google.android.apps.maps")
            }

            // Check if Google Maps is installed
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                // Fallback: open in browser if Google Maps is not installed
                val browserUri = Uri.parse(
                    "https://www.google.com/maps/dir/?api=1&origin=$userLat,$userLng&destination=$destLat,$destLng&travelmode=driving"
                )
                startActivity(Intent(Intent.ACTION_VIEW, browserUri))
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Could not open Google Maps: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
