package com.wildtribe.drive.ble

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import android.telecom.TelecomManager
import com.wildtribe.drive.util.LogHelper
import com.wildtribe.drive.util.PreferenceHelper

/**
 * Intercepts phone notifications and broadcasts them to the Dashboard.
 *
 * User must grant Notification Access in System Settings for this to work.
 * Controlled by the "Phone Notifications" toggle in app Settings.
 */
class WildTribeNotificationService : NotificationListenerService() {

    private val callPackages = setOf(
        "com.android.phone",
        "com.google.android.dialer",
        "com.samsung.android.incallui",
        "com.miui.incallui"
    )

    private val messagePackages = setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "org.whatsapp",
        "com.facebook.messenger",
        "com.instagram.android",
        "com.telegram.messenger"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!PreferenceHelper.isNotificationsEnabled(applicationContext)) return

        val pkg = sbn.packageName
        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        val displayTitle = when {
            pkg in callPackages -> "📞 $title"
            pkg in messagePackages -> "💬 $title"
            else -> title
        }

        if (displayTitle.isBlank()) return

        LogHelper.d("NotifService", "Notification from $pkg: $displayTitle – $text")
        broadcastNotification(displayTitle, text)
    }

    private fun broadcastNotification(title: String, text: String) {
        val intent = Intent(BleConstants.ACTION_PHONE_NOTIFICATION).apply {
            putExtra(BleConstants.EXTRA_NOTIFICATION_TITLE, title)
            putExtra(BleConstants.EXTRA_NOTIFICATION_TEXT, text)
        }
        sendBroadcast(intent)
    }
}
