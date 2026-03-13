package com.wildtribe.drive.data

import android.content.Context
import android.content.SharedPreferences
import com.wildtribe.drive.utils.DebugLogger

/**
 * Manages ride session state and persistent preferences.
 */
class RideRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ride_prefs", Context.MODE_PRIVATE)

    var currentSession: RideSession? = null
        private set

    // ── Preference keys ───────────────────────────────────────────────────

    var savedDeviceAddress: String?
        get() = prefs.getString("saved_device_address", null)
        set(v) = prefs.edit().putString("saved_device_address", v).apply()

    var savedDeviceName: String?
        get() = prefs.getString("saved_device_name", null)
        set(v) = prefs.edit().putString("saved_device_name", v).apply()

    var useKph: Boolean
        get() = prefs.getBoolean("use_kph", true)
        set(v) = prefs.edit().putBoolean("use_kph", v).apply()

    var speedWarningLimit: Int
        get() = prefs.getInt("warning_limit", 100)
        set(v) = prefs.edit().putInt("warning_limit", v).apply()

    var vibrateOnWarning: Boolean
        get() = prefs.getBoolean("vibrate_warning", true)
        set(v) = prefs.edit().putBoolean("vibrate_warning", v).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)
        set(v) = prefs.edit().putBoolean("notifications_enabled", v).apply()

    var screenTimeout: String
        get() = prefs.getString("screen_timeout", "always_on") ?: "always_on"
        set(v) = prefs.edit().putString("screen_timeout", v).apply()

    var rideCount: Int
        get() = prefs.getInt("ride_count", 0)
        set(v) = prefs.edit().putInt("ride_count", v).apply()

    var firstInstallDate: Long
        get() = prefs.getLong("first_install_date", System.currentTimeMillis())
        set(v) = prefs.edit().putLong("first_install_date", v).apply()

    var reviewPrompted: Boolean
        get() = prefs.getBoolean("review_prompted", false)
        set(v) = prefs.edit().putBoolean("review_prompted", v).apply()

    var onboardingShown: Boolean
        get() = prefs.getBoolean("onboarding_shown", false)
        set(v) = prefs.edit().putBoolean("onboarding_shown", v).apply()

    // ── Session management ────────────────────────────────────────────────

    fun startSession(): RideSession {
        val session = RideSession()
        currentSession = session
        rideCount++
        DebugLogger.log("RIDE", "Session started. Total rides: $rideCount")
        return session
    }

    fun endSession() {
        val session = currentSession
        if (session != null) {
            DebugLogger.log(
                "RIDE",
                "Session ended. Duration=${session.formattedElapsed()} " +
                "MaxSpeed=${session.maxSpeedKph}kph AvgSpeed=${session.avgSpeedKph}kph"
            )
        }
        currentSession = null
    }

    fun forgetDevice() {
        savedDeviceAddress = null
        savedDeviceName = null
        DebugLogger.log("PREF", "Device forgotten")
    }
}
