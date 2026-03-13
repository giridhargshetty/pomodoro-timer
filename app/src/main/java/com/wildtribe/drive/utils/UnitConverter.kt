package com.wildtribe.drive.utils

import kotlin.math.roundToInt

/**
 * Speed unit conversion utilities.
 *
 * FIX 2: Auto-convert stored warning limit when unit changes.
 * TEST: Unit conversion: 100 km/h warning → 62 mph after unit switch
 */
object UnitConverter {

    fun kphToMph(kph: Int): Int = (kph * 0.621371).roundToInt()
    fun mphToKph(mph: Int): Int = (mph * 1.60934).roundToInt()

    fun kphToMph(kph: Double): Double = kph * 0.621371
    fun mphToKph(mph: Double): Double = mph * 1.60934

    /**
     * Convert warning limit when the user switches speed units.
     *
     * @param currentLimit the currently stored limit in the OLD unit
     * @param switchingToKph true if switching TO km/h (so current limit was in mph)
     */
    fun convertWarningLimit(currentLimit: Int, switchingToKph: Boolean): Int {
        return if (switchingToKph) mphToKph(currentLimit) else kphToMph(currentLimit)
    }

    /** Format speed value for display. */
    fun formatSpeed(kph: Int, useKph: Boolean): String {
        return if (useKph) kph.toString() else kphToMph(kph).toString()
    }

    /** Unit label for display. */
    fun unitLabel(useKph: Boolean): String = if (useKph) "km/h" else "mph"
}
