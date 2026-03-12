package com.wildtribe.drive.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wildtribe.drive.ble.BleConstants
import com.wildtribe.drive.ble.BleService
import com.wildtribe.drive.util.LogHelper

/**
 * Receives navigation commands from Tasker automations.
 *
 * Tasker should send a broadcast with one of these actions:
 *   - com.wildtribe.drive.SEND_COMMAND   → extra: "command" (String)
 *   - com.wildtribe.drive.NAV_COMMAND    → extra: "command" (String)
 *   - com.wildtribe.drive.SPEED_UPDATE   → extra: "command" = "SPD:XX"
 *
 * Example Tasker action:
 *   Action: com.wildtribe.drive.SEND_COMMAND
 *   Extra:  command:NAV:R:300m
 */
class TaskerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(BleConstants.EXTRA_COMMAND)?.trim()
        if (command.isNullOrBlank()) {
            LogHelper.w("TaskerReceiver", "Received empty command from Tasker – ignoring")
            return
        }

        LogHelper.d("TaskerReceiver", "Received Tasker command: $command")

        // Forward to BleService which owns the BLE connection
        val serviceIntent = BleService.buildSendIntent(context, command)
        context.startForegroundService(serviceIntent)
    }
}
