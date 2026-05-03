package com.fuelqueue.ui.map

import androidx.navigation.NavDirections

// Navigation directions from Map → Detail
object MapFragmentDirections {
    fun actionMapToDetail(stationId: Long): NavDirections {
        return object : NavDirections {
            override val actionId = com.fuelqueue.R.id.action_map_to_detail
            override val arguments = androidx.core.os.bundleOf("stationId" to stationId)
        }
    }
}
