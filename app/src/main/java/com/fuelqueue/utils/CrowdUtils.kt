package com.fuelqueue.utils

import android.graphics.Color
import com.fuelqueue.R

object CrowdUtils {

    fun getColor(crowdLevel: String): Int = when (crowdLevel) {
        "LOW"    -> Color.parseColor("#2E7D32")   // dark green
        "MEDIUM" -> Color.parseColor("#F57F17")   // amber
        "HIGH"   -> Color.parseColor("#C62828")   // red
        else     -> Color.parseColor("#607D8B")   // grey
    }

    fun getEmoji(crowdLevel: String): String = when (crowdLevel) {
        "LOW"    -> "🟢"
        "MEDIUM" -> "🟡"
        "HIGH"   -> "🔴"
        else     -> "⚪"
    }

    fun getLabel(crowdLevel: String): String = when (crowdLevel) {
        "LOW"    -> "QUIET — Go now!"
        "MEDIUM" -> "MODERATE — Short wait"
        "HIGH"   -> "BUSY — Long queue!"
        else     -> "Unknown"
    }

    fun getAdvice(crowdLevel: String): String = when (crowdLevel) {
        "LOW"    -> "Great time to fill up! Very few vehicles here."
        "MEDIUM" -> "Expect a short wait of a few minutes."
        "HIGH"   -> "This station is very busy. Consider another nearby station."
        else     -> "Crowd information unavailable."
    }
}
