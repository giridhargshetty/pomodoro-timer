package com.wildtribe.drive.data

/**
 * Represents the current riding session data.
 */
data class RideSession(
    val startTimeMs: Long = System.currentTimeMillis(),
    var currentSpeedKph: Int = 0,
    var totalDistanceM: Double = 0.0,
    var maxSpeedKph: Int = 0,
    var speedSamples: Int = 0,
    var totalSpeedSum: Int = 0
) {
    val elapsedMs: Long
        get() = System.currentTimeMillis() - startTimeMs

    val avgSpeedKph: Int
        get() = if (speedSamples > 0) totalSpeedSum / speedSamples else 0

    fun updateSpeed(kph: Int) {
        currentSpeedKph = kph
        if (kph > maxSpeedKph) maxSpeedKph = kph
        if (kph > 0) {
            speedSamples++
            totalSpeedSum += kph
        }
    }

    fun formattedElapsed(): String {
        val totalSecs = elapsedMs / 1000
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
