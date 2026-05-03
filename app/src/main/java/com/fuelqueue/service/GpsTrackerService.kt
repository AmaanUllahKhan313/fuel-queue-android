package com.fuelqueue.service

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fuelqueue.data.api.RetrofitClient
import com.fuelqueue.data.model.LocationPing
import com.fuelqueue.ui.MainActivity
import com.fuelqueue.utils.SessionManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class GpsTrackerService : Service() {

    companion object {
        const val CHANNEL_ID    = "gps_tracker_channel"
        const val NOTIF_ID      = 1001
        const val TAG           = "GpsTrackerService"
        const val PING_INTERVAL = 10_000L
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // Check permission before starting foreground
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            Log.w(TAG, "Location permission not granted — GPS service not starting")
            stopSelf()
            return
        }

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "GPS tracking service started")
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, PING_INTERVAL)
            .setMinUpdateIntervalMillis(5_000L)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied: ${e.message}")
            stopSelf()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val userId   = SessionManager.getUserId()
            if (userId == -1L) return

            scope.launch {
                try {
                    val ping = LocationPing(
                        userId    = userId,
                        latitude  = location.latitude,
                        longitude = location.longitude,
                        speedKmh  = location.speed * 3.6,
                        timestamp = System.currentTimeMillis()
                    )
                    val response = RetrofitClient.api.sendLocationPing(ping)
                    if (response.isSuccessful && response.body() != null) {
                        Log.d(TAG, "At station: ${response.body()!!.stationName} | ${response.body()!!.crowdLevel}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Ping failed: ${e.message}")
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val intent        = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⛽ Fuel Queue Active")
            .setContentText("Helping others find quiet stations nearby")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "GPS Location Tracker",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (::fusedClient.isInitialized) {
            fusedClient.removeLocationUpdates(locationCallback)
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}