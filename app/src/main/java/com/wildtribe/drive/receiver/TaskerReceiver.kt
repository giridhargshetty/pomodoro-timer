package com.wildtribe.drive.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wildtribe.drive.ble.BleConstants
import com.wildtribe.drive.ble.BleService
import com.wildtribe.drive.utils.DebugLogger

/**
 * Receives navigation commands from Tasker automations.
 *
 * Tasker → Broadcast:
 *   Action: com.wildtribe.drive.SEND_COMMAND
 *   Extra:  command = "NAV:R:300m" (or any BLE command string)
 *
 * Supports all command types: NAV:, SPD:, MSG:, TRP:, CLR
 */
class TaskerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(BleConstants.EXTRA_COMMAND)?.trim()
        if (command.isNullOrBlank()) {
            DebugLogger.w("TASKER", "Received empty command — ignoring")
            return
        }

        // Log every Tasker command
        DebugLogger.log("TASKER", "Received command: '$command'")

        // Forward to BleService which owns the BLE connection
        context.startForegroundService(BleService.buildSendIntent(context, command))
    }
}
