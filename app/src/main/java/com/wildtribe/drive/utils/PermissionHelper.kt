package com.wildtribe.drive.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.wildtribe.drive.accessibility.MapsAccessibilityService

/**
 * Centralised permission check helpers.
 */
object PermissionHelper {

    /** Returns array of BLE permissions required for current API level. */
    fun blePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    fun locationPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun hasBlePermissions(context: Context): Boolean =
        blePermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PermissionChecker.PERMISSION_GRANTED
        }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PermissionChecker.PERMISSION_GRANTED

    /**
     * Check if MapsAccessibilityService is enabled.
     * TEST: Accessibility service starts after permission granted
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/${MapsAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { it.equals(service, ignoreCase = true) }
    }

    /** Open the system Accessibility Settings page. */
    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** Check if notification listener service is enabled. */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName)
    }

    /** Open notification listener access settings. */
    fun openNotificationListenerSettings(context: Context) {
        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
