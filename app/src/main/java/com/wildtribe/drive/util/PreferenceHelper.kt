package com.wildtribe.drive.util

import android.content.Context
import android.content.SharedPreferences
import com.wildtribe.drive.ble.BleConstants

/**
 * Centralized SharedPreferences access layer.
 * All reads/writes go through this object for consistency and testability.
 */
object PreferenceHelper {

    private const val PREFS_NAME = "wildtribe_prefs"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Device ───────────────────────────────────────────────────────────────

    fun saveDeviceAddress(context: Context, address: String) =
        prefs(context).edit().putString(BleConstants.PREF_SAVED_DEVICE_ADDRESS, address).apply()

    fun saveDeviceName(context: Context, name: String) =
        prefs(context).edit().putString(BleConstants.PREF_SAVED_DEVICE_NAME, name).apply()

    fun getSavedDeviceAddress(context: Context): String? =
        prefs(context).getString(BleConstants.PREF_SAVED_DEVICE_ADDRESS, null)

    fun getSavedDeviceName(context: Context): String? =
        prefs(context).getString(BleConstants.PREF_SAVED_DEVICE_NAME, null)

    fun forgetDevice(context: Context) {
        prefs(context).edit()
            .remove(BleConstants.PREF_SAVED_DEVICE_ADDRESS)
            .remove(BleConstants.PREF_SAVED_DEVICE_NAME)
            .apply()
    }

    fun hasSavedDevice(context: Context): Boolean =
        getSavedDeviceAddress(context) != null

    // ─── Speed Settings ───────────────────────────────────────────────────────

    fun saveSpeedUnit(context: Context, unit: String) =
        prefs(context).edit().putString(BleConstants.PREF_SPEED_UNIT, unit).apply()

    fun getSpeedUnit(context: Context): String =
        prefs(context).getString(BleConstants.PREF_SPEED_UNIT, BleConstants.DEFAULT_SPEED_UNIT)
            ?: BleConstants.DEFAULT_SPEED_UNIT

    fun saveSpeedWarningLimit(context: Context, limit: Int) =
        prefs(context).edit().putInt(BleConstants.PREF_SPEED_WARNING_LIMIT, limit).apply()

    fun getSpeedWarningLimit(context: Context): Int =
        prefs(context).getInt(
            BleConstants.PREF_SPEED_WARNING_LIMIT,
            BleConstants.DEFAULT_SPEED_WARNING_KMH
        )

    // ─── Notifications ────────────────────────────────────────────────────────

    fun setNotificationsEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(BleConstants.PREF_NOTIFICATIONS_ENABLED, enabled).apply()

    fun isNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(BleConstants.PREF_NOTIFICATIONS_ENABLED, true)

    // ─── Review Tracking ──────────────────────────────────────────────────────

    fun incrementRideCount(context: Context) {
        val current = getRideCount(context)
        prefs(context).edit().putInt(BleConstants.PREF_RIDE_COUNT, current + 1).apply()
    }

    fun getRideCount(context: Context): Int =
        prefs(context).getInt(BleConstants.PREF_RIDE_COUNT, 0)

    fun setFirstInstallDate(context: Context, timestamp: Long) {
        if (prefs(context).getLong(BleConstants.PREF_FIRST_INSTALL_DATE, -1L) == -1L) {
            prefs(context).edit().putLong(BleConstants.PREF_FIRST_INSTALL_DATE, timestamp).apply()
        }
    }

    fun getFirstInstallDate(context: Context): Long =
        prefs(context).getLong(BleConstants.PREF_FIRST_INSTALL_DATE, System.currentTimeMillis())

    fun setReviewPrompted(context: Context, prompted: Boolean) =
        prefs(context).edit().putBoolean(BleConstants.PREF_REVIEW_PROMPTED, prompted).apply()

    fun hasBeenPromptedForReview(context: Context): Boolean =
        prefs(context).getBoolean(BleConstants.PREF_REVIEW_PROMPTED, false)
}
