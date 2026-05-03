package com.fuelqueue.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.fuelqueue.R
import com.fuelqueue.databinding.ActivityMainBinding
import com.fuelqueue.service.GpsTrackerService
import com.fuelqueue.utils.SessionManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Permission launcher — asks user for location access
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            startGpsTracking()
        } else {
            Toast.makeText(this,
                "Location permission needed to show crowd levels",
                Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.init(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment, R.id.registerFragment ->
                    binding.bottomNav.visibility = android.view.View.GONE
                else ->
                    binding.bottomNav.visibility = android.view.View.VISIBLE
            }
        }

        binding.bottomNav.setupWithNavController(navController)

        // Request location permission then start GPS if already logged in
        if (SessionManager.isLoggedIn()) {
            requestLocationAndStartTracking()
        }
    }

    fun requestLocationAndStartTracking() {
        when {
            // Already have permission — start immediately
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startGpsTracking()
            }
            // Ask user for permission
            else -> {
                locationPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    fun startGpsTracking() {
        val intent = Intent(this, GpsTrackerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun stopGpsTracking() {
        stopService(Intent(this, GpsTrackerService::class.java))
    }
}