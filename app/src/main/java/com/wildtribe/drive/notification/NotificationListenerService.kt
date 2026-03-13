package com.wildtribe.drive.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.wildtribe.drive.ble.BleConstants
import com.wildtribe.drive.ble.BleService
import com.wildtribe.drive.data.RideRepository
import com.wildtribe.drive.utils.DebugLogger

/**
 * FIX 5: Generic notification listener — no hardcoded package list.
 * Filters by notification CONTENT (category, keywords) instead of app package name,
 * so it works with WhatsApp, Telegram, Signal, Phone, etc. automatically.
 *
 * TEST: Notification from WhatsApp appears on dashboard for 5 seconds
 */
class NotificationListenerService : NotificationListenerService() {

    /** Packages that should never be forwarded to the display. */
    private val excludedPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.wildtribe.drive",
        "com.google.android.gms",
        "com.android.launcher3"
    )

    private var bleService: BleService? = null
    private var repo: RideRepository? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bleService = (service as? BleService.LocalBinder)?.getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        repo = RideRepository(applicationContext)
        // Bind to BleService for command forwarding
        bindService(
            Intent(this, BleService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
        DebugLogger.log("NOTIF_SVC", "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try { unbindService(serviceConnection) } catch (_: Exception) {}
        DebugLogger.log("NOTIF_SVC", "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName

        // Skip excluded system packages
        if (pkg in excludedPackages) return

        // Skip if notifications are disabled in settings
        if (repo?.notificationsEnabled == false) return

        val notification = sbn.notification
        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text  = notification.extras.getString(Notification.EXTRA_TEXT)  ?: ""

        // FIX 5: Detect by CONTENT, not package name
        val isCall = title.contains("calling", ignoreCase = true) ||
                     text.contains("incoming call", ignoreCase = true) ||
                     notification.category == Notification.CATEGORY_CALL

        val isMsg  = notification.category == Notification.CATEGORY_MESSAGE ||
                     notification.category == Notification.CATEGORY_EMAIL ||
                     // Heuristic: short text likely a message
                     (title.isNotBlank() && text.isNotBlank() && text.length < 160)

        if (!isCall && !isMsg) return
        if (title.isBlank()) return

        val display = when {
            isCall -> "CALL: $title"
            else   -> "$title: ${text.take(30)}"
        }

        val cmd = BleConstants.msgCmd(display)
        DebugLogger.log("NOTIF", "Forwarded from $pkg: $display")

        // Forward to BLE device if connected
        bleService?.sendCommandAndBroadcast(cmd) ?: run {
            // If service not bound, broadcast the command directly
            sendBroadcast(Intent(BleConstants.ACTION_NAVIGATION_COMMAND).apply {
                putExtra(BleConstants.EXTRA_COMMAND, cmd)
            })
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not used
    }
}
