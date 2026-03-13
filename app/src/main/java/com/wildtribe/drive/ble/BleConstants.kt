package com.wildtribe.drive.ble

import java.util.UUID

/**
 * BLE protocol constants and command builders for MotoRound ESP32-S3.
 */
object BleConstants {

    // ── Device ────────────────────────────────────────────────────────────
    const val DEVICE_NAME           = "MotoRound"
    const val MOTOROUND_DEVICE_NAME = "MotoRound"   // alias
    val SERVICE_UUID: UUID          = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    val CHARACTERISTIC_UUID: UUID   = UUID.fromString("abcdef01-1234-1234-1234-123456789abc")

    // ── Timing ────────────────────────────────────────────────────────────
    const val SCAN_PERIOD_MS        = 15_000L
    const val GATT_TIMEOUT_MS       = 10_000L  // FIX 6: 10s timeout on service discovery
    const val GATT_CONNECT_TIMEOUT_MS = 10_000L
    const val BASE_RECONNECT_MS     = 3_000L   // FIX 3: exponential backoff base
    const val MAX_RECONNECT_MS      = 60_000L  // FIX 3: max backoff cap
    const val MAX_RECONNECT_TRIES   = 10       // FIX 3: give up after 10 attempts
    const val NAV_TIMEOUT_MS        = 10_000L

    // ── Notification channels ─────────────────────────────────────────────
    const val NOTIFICATION_CHANNEL_ID   = "wildtribe_ble_service"
    const val NOTIFICATION_CHANNEL_NAME = "Wild Tribe Drive — Riding"
    const val FOREGROUND_NOTIFICATION_ID = 1001
    const val INCOMING_MSG_NOTIFICATION_ID = 1002

    // ── Intent Actions ────────────────────────────────────────────────────
    const val ACTION_BLE_STATE_CHANGED  = "com.wildtribe.drive.BLE_STATE_CHANGED"
    const val ACTION_NAVIGATION_COMMAND = "com.wildtribe.drive.NAVIGATION_COMMAND"
    const val ACTION_PHONE_NOTIFICATION = "com.wildtribe.drive.PHONE_NOTIFICATION"
    const val ACTION_SEND_COMMAND       = "com.wildtribe.drive.SEND_COMMAND"

    // ── Intent Extras ─────────────────────────────────────────────────────
    const val EXTRA_CONNECTION_STATE    = "connection_state"
    const val EXTRA_COMMAND             = "command"
    const val EXTRA_DEVICE_NAME         = "device_name"
    const val EXTRA_DEVICE_ADDRESS      = "device_address"
    const val EXTRA_NOTIFICATION_TITLE  = "notification_title"
    const val EXTRA_NOTIFICATION_TEXT   = "notification_text"
    const val EXTRA_RSSI                = "rssi"

    // ── SharedPreferences Keys ────────────────────────────────────────────
    const val PREF_SAVED_DEVICE_ADDRESS = "saved_device_address"
    const val PREF_SAVED_DEVICE_NAME    = "saved_device_name"
    const val PREF_SPEED_UNIT           = "speed_unit"
    const val PREF_SPEED_WARNING_LIMIT  = "speed_warning_limit"
    const val PREF_NOTIFICATIONS_ENABLED= "notifications_enabled"
    const val PREF_RIDE_COUNT           = "ride_count"
    const val PREF_FIRST_INSTALL_DATE   = "first_install_date"
    const val PREF_REVIEW_PROMPTED      = "review_prompted"

    // ── Defaults ──────────────────────────────────────────────────────────
    const val DEFAULT_SPEED_UNIT        = "km/h"
    const val DEFAULT_SPEED_WARNING_KMH = 100
    const val DEFAULT_SPEED_WARNING_MPH = 65

    // ── Tasker ────────────────────────────────────────────────────────────
    const val TASKER_ACTION_SEND_COMMAND = "com.wildtribe.drive.SEND_COMMAND"

    // ── Command Builders ──────────────────────────────────────────────────

    /** Turn navigation command: NAV:R:300m */
    fun navCmd(dir: String, dist: String) = "NAV:$dir:$dist"

    /** Speed command: SPD:65 */
    fun spdCmd(kph: Int) = "SPD:$kph"

    /** Message/notification command: MSG:<text up to 50 chars> */
    fun msgCmd(text: String) = "MSG:${text.take(50)}"

    /** Trip info command: TRP:45km:1h23m:18:30 */
    fun trpCmd(dist: String, duration: String, eta: String) = "TRP:$dist:$duration:$eta"

    /** Clear display */
    const val CLR_CMD = "CLR"
    const val CMD_CLEAR = "CLR"
}

// ConnectionState is defined in com.wildtribe.drive.model.ConnectionState
