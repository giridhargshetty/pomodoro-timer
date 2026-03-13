package com.wildtribe.drive.ble

import com.wildtribe.drive.accessibility.NavData

/**
 * Converts [NavData] from the accessibility service into BLE command strings
 * ready to send to the MotoRound ESP32-S3.
 */
object BleCommandBuilder {

    /**
     * Build a NAV command from accessibility NavData.
     * Returns null if there is no navigation turn to send.
     */
    fun fromNavData(navData: NavData): String? {
        val turn = navData.turn ?: return null
        val dist = navData.distance ?: "0"
        return BleConstants.navCmd(turn, dist)
    }

    /**
     * Build a TRP command from trip fields.
     */
    fun fromTripData(remaining: String, duration: String, eta: String): String {
        return BleConstants.trpCmd(remaining, duration, eta)
    }

    /**
     * Build a MSG command from a notification.
     */
    fun fromNotification(title: String, text: String, isCall: Boolean): String {
        val display = if (isCall) "CALL: $title" else "$title: ${text.take(30)}"
        return BleConstants.msgCmd(display)
    }
}
