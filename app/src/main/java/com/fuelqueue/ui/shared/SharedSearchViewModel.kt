package com.fuelqueue.ui.shared

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedSearchViewModel : ViewModel() {
    // Default 10km radius at zoom level 14
    private val _searchRadiusMeters = MutableStateFlow(10000.0)
    val searchRadiusMeters: StateFlow<Double> = _searchRadiusMeters

    fun updateSearchRadius(radiusMeters: Double) {
        _searchRadiusMeters.value = radiusMeters
    }

    fun getSearchRadius(): Double = _searchRadiusMeters.value
}

