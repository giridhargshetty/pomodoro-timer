package com.wildtribe.drive.ble

import java.util.UUID

/**
 * BLE protocol constants for MotoRound device communication.
 * All UUIDs and command formats are defined here for easy maintenance.
 */
object BleConstants {

    // ─── Device Identity ──────────────────────────────────────────────────────
    const val MOTOROUND_DEVICE_NAME = "MotoRound"

    // ─── GATT UUIDs ───────────────────────────────────────────────────────────
    val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("abcdef01-1234-1234-1234-123456789abc")

    // ─── BLE Scan Settings ────────────────────────────────────────────────────
    const val SCAN_PERIOD_MS = 15_000L          // 15 seconds scan window
    const val RECONNECT_DELAY_MS = 3_000L       // Retry every 3 seconds
    const val GATT_CONNECT_TIMEOUT_MS = 10_000L // 10 second connection timeout
    const val NAV_TIMEOUT_MS = 10_000L          // Show "Waiting…" after 10s of silence

    // ─── Navigation Commands ──────────────────────────────────────────────────
    const val CMD_NAV_RIGHT = "NAV:R"           // NAV:R:300m
    const val CMD_NAV_LEFT = "NAV:L"            // NAV:L:1.2km
    const val CMD_NAV_STRAIGHT = "NAV:S"        // NAV:S:0
    const val CMD_NAV_UTURN = "NAV:UT"          // NAV:UT:
    const val CMD_NAV_ARRIVE = "NAV:AR"         // NAV:AR:
    const val CMD_SPEED = "SPD"                 // SPD:65
    const val CMD_MESSAGE = "MSG"               // MSG:Ravi calling
    const val CMD_TRIP = "TRP"                  // TRP:45km:1h23m:18:30
    const val CMD_CLEAR = "CLR"                 // CLR

    // ─── Service Notification Channel ─────────────────────────────────────────
    const val NOTIFICATION_CHANNEL_ID = "wildtribe_ble_service"
    const val NOTIFICATION_CHANNEL_NAME = "BLE Connection Service"
    const val FOREGROUND_NOTIFICATION_ID = 1001
    const val INCOMING_MSG_NOTIFICATION_ID = 1002

    // ─── Intent Actions (BleService ↔ Activities) ─────────────────────────────
    const val ACTION_BLE_STATE_CHANGED = "com.wildtribe.drive.BLE_STATE_CHANGED"
    const val ACTION_NAVIGATION_COMMAND = "com.wildtribe.drive.NAVIGATION_COMMAND"
    const val ACTION_PHONE_NOTIFICATION = "com.wildtribe.drive.PHONE_NOTIFICATION"

    // ─── Intent Extras ────────────────────────────────────────────────────────
    const val EXTRA_CONNECTION_STATE = "connection_state"
    const val EXTRA_DEVICE_NAME = "device_name"
    const val EXTRA_DEVICE_ADDRESS = "device_address"
    const val EXTRA_COMMAND = "command"
    const val EXTRA_NOTIFICATION_TITLE = "notification_title"
    const val EXTRA_NOTIFICATION_TEXT = "notification_text"
    const val EXTRA_RSSI = "rssi"

    // ─── SharedPreferences Keys ────────────────────────────────────────────────
    const val PREF_SAVED_DEVICE_ADDRESS = "saved_device_address"
    const val PREF_SAVED_DEVICE_NAME = "saved_device_name"
    const val PREF_SPEED_UNIT = "speed_unit"
    const val PREF_SPEED_WARNING_LIMIT = "speed_warning_limit"
    const val PREF_NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val PREF_RIDE_COUNT = "ride_count"
    const val PREF_FIRST_INSTALL_DATE = "first_install_date"
    const val PREF_REVIEW_PROMPTED = "review_prompted"

    // ─── Defaults ─────────────────────────────────────────────────────────────
    const val DEFAULT_SPEED_UNIT = "km/h"
    const val DEFAULT_SPEED_WARNING_KMH = 100
    const val DEFAULT_SPEED_WARNING_MPH = 65

    // ─── Tasker Intent Actions (external → app) ───────────────────────────────
    const val TASKER_ACTION_SEND_COMMAND = "com.wildtribe.drive.SEND_COMMAND"
    const val TASKER_ACTION_NAV_COMMAND = "com.wildtribe.drive.NAV_COMMAND"
    const val TASKER_ACTION_SPEED_UPDATE = "com.wildtribe.drive.SPEED_UPDATE"
}
