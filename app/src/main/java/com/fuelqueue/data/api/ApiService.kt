package com.fuelqueue.data.api

import com.fuelqueue.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<MessageResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ── Stations ──────────────────────────────────────────────────────────────

    @GET("api/stations")
    suspend fun getAllStations(): Response<List<Station>>

    @GET("api/stations/nearby")
    suspend fun getNearbyStations(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Double = 10000.0
    ): Response<List<Station>>

    @GET("api/stations/{id}/crowd")
    suspend fun getCrowdStatus(@Path("id") stationId: Long): Response<CrowdStatus>

    // ── GPS ───────────────────────────────────────────────────────────────────

    @POST("api/gps/ping")
    suspend fun sendLocationPing(@Body ping: LocationPing): Response<CrowdStatus?>
}
