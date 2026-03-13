package com.wildtribe.drive.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wildtribe.drive.ble.BleService
import com.wildtribe.drive.data.RideRepository
import com.wildtribe.drive.utils.DebugLogger

/**
 * Auto-starts BLE service after device reboot.
 * Only reconnects if a device was previously paired.
 *
 * TEST: Boot receiver starts BleService after phone restart
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val repo = RideRepository(context)
                if (repo.savedDeviceAddress != null) {
                    DebugLogger.log("BOOT", "Boot complete — starting BLE service to reconnect")
                    context.startForegroundService(BleService.buildConnectIntent(context))
                } else {
                    DebugLogger.log("BOOT", "Boot complete — no saved device, skipping auto-start")
                }
            }
        }
    }
}
