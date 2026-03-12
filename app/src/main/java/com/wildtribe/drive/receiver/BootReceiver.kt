package com.wildtribe.drive.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wildtribe.drive.ble.BleService
import com.wildtribe.drive.util.LogHelper
import com.wildtribe.drive.util.PreferenceHelper

/**
 * Starts the BLE service automatically after device boot,
 * so the MotoRound reconnects without user intervention.
 *
 * Only starts if a device was previously saved.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (PreferenceHelper.hasSavedDevice(context)) {
                    LogHelper.i("BootReceiver", "Boot complete – starting BLE service")
                    val serviceIntent = BleService.buildConnectIntent(context)
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }
}
