package com.fuelqueue.data.model

data class Station(
    val stationId: Long = 0,
    val id: Long = 0,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double = 0.0,
    val crowdLevel: String = "",       // "LOW" | "MEDIUM" | "HIGH"
    val activeUsers: Int = 0,
    val estimatedWaitMinutes: Int = 0,
    val updatedAt: Long = 0L,
    val geofenceRadiusMeters: Double = 0.0,
    val active: Boolean = true,
    val live: Boolean = false   // Live station indicator from backend
)

data class CrowdStatus(
    val stationId: Long,
    val stationName: String,
    val activeUsers: Int,
    val crowdLevel: String,
    val estimatedWaitMinutes: Int,
    val updatedAt: Long
)

data class LocationPing(
    val userId: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val timestamp: Long = System.currentTimeMillis()
)

// ── OTP Authentication ──────────────────────────────────────────

data class SendOtpRequest(
    val phoneNumber: String
)

data class VerifyOtpRequest(
    val phoneNumber: String,
    val otp: String,
    val name: String? = null  // Optional: for registration
)

data class OtpResponse(
    val success: Boolean,
    val message: String,
    val phoneNumber: String,
    val otp: String? = null  // Only present in testing/development
)

data class LoginResponse(
    val token: String,
    val userId: Long,
    val name: String,
    val phoneNumber: String
)

// ── Legacy (deprecated, kept for backward compatibility) ──

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class MessageResponse(
    val message: String?,
    val error: String?
)
