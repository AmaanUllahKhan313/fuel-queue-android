package com.fuelqueue.data.model

data class Station(
    val stationId: Long,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val crowdLevel: String,       // "LOW" | "MEDIUM" | "HIGH"
    val activeUsers: Int,
    val estimatedWaitMinutes: Int
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

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class LoginResponse(
    val token: String,
    val userId: Long,
    val name: String
)

data class MessageResponse(
    val message: String?,
    val error: String?
)
