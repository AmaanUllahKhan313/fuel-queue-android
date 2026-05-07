package com.fuelqueue.utils

object CrowdUtils {

    private fun normalize(crowdLevel: String): String = crowdLevel.trim().uppercase()

    fun getColor(crowdLevel: String): Int = when (normalize(crowdLevel)) {
        "LOW"    -> 0xFF1DB584.toInt()      // Teal from new palette
        "MEDIUM" -> 0xFFF59E0B.toInt()      // Amber/Orange
        "HIGH"   -> 0xFFEF4444.toInt()      // Red
        else     -> 0xFF9CA3AF.toInt()      // Gray
    }

    fun getEmoji(crowdLevel: String): String = when (normalize(crowdLevel)) {
        "LOW"    -> "🟢"
        "MEDIUM" -> "🟡"
        "HIGH"   -> "🔴"
        else     -> "⚪"
    }

    fun getLabel(crowdLevel: String): String = when (normalize(crowdLevel)) {
        "LOW"    -> "QUIET — Go now!"
        "MEDIUM" -> "MODERATE — Short wait"
        "HIGH"   -> "BUSY — Long queue!"
        else     -> "Unknown"
    }

    fun getAdvice(crowdLevel: String): String = when (normalize(crowdLevel)) {
        "LOW"    -> "Great time to fill up! Very few vehicles here."
        "MEDIUM" -> "Expect a short wait of a few minutes."
        "HIGH"   -> "This station is very busy. Consider another nearby station."
        else     -> "Crowd information unavailable."
    }
}
