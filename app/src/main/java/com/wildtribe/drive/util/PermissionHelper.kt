package com.wildtribe.drive.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper for checking and requesting BLE + notification permissions
 * across different Android API levels.
 */
object PermissionHelper {

    const val REQUEST_BLE_PERMISSIONS = 100
    const val REQUEST_NOTIFICATION_PERMISSION = 101

    /**
     * All permissions required for BLE scanning and connection.
     * Adapts based on the running Android version.
     */
    fun blePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    fun notificationPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    /** Returns true if all BLE permissions are granted. */
    fun hasBlePermissions(context: Context): Boolean =
        blePermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    /** Returns true if notification permission is granted (always true on API < 33). */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /** Request all BLE permissions from the given activity. */
    fun requestBlePermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            blePermissions(),
            REQUEST_BLE_PERMISSIONS
        )
    }

    /** Request notification permission (Android 13+ only). */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    /** Returns true if all granted. */
    fun allGranted(grantResults: IntArray): Boolean =
        grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
}
