package com.wildtribe.drive.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wildtribe.drive.R
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.ui.dashboard.DashboardActivity
import com.wildtribe.drive.util.LogHelper
import com.wildtribe.drive.util.PreferenceHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Foreground service that keeps the BLE connection alive when the app is in the background.
 *
 * Responsibilities:
 *  - Owns the [BleManager] singleton lifecycle
 *  - Starts as a foreground service with a persistent notification
 *  - Receives raw commands and broadcasts them to the Dashboard
 *  - Triggers auto-reconnect to the last saved device
 */
class BleService : Service() {

    // ─── Binder ───────────────────────────────────────────────────────────────
    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    private val binder = LocalBinder()

    // ─── Dependencies ─────────────────────────────────────────────────────────
    lateinit var bleManager: BleManager
        private set

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(applicationContext)
        createNotificationChannel()
        startForeground(BleConstants.FOREGROUND_NOTIFICATION_ID, buildNotification("Disconnected"))
        observeConnectionState()
        LogHelper.d("BleService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(BleConstants.EXTRA_DEVICE_ADDRESS) ?: run {
                    // Auto-reconnect to saved device
                    PreferenceHelper.getSavedDeviceAddress(this)
                }
                address?.let { bleManager.connect(it) }
            }
            ACTION_DISCONNECT -> bleManager.disconnect()
            ACTION_SEND_COMMAND -> {
                val cmd = intent.getStringExtra(BleConstants.EXTRA_COMMAND) ?: return START_STICKY
                sendCommandAndBroadcast(cmd)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        serviceScope.cancel()
        bleManager.release()
        super.onDestroy()
        LogHelper.d("BleService", "Service destroyed")
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Send a command to the MotoRound device and broadcast it to the Dashboard. */
    fun sendCommandAndBroadcast(command: String) {
        val sent = bleManager.sendCommand(command)
        LogHelper.d("BleService", "Send '$command' → success=$sent")
        broadcastCommand(command)
    }

    /** Initiate connection to a specific device address. */
    fun connectToDevice(address: String) {
        PreferenceHelper.saveDeviceAddress(this, address)
        bleManager.connect(address)
    }

    // ─── Connection State Observer ────────────────────────────────────────────

    private fun observeConnectionState() {
        serviceScope.launch {
            bleManager.connectionState.collectLatest { state ->
                updateNotification(state)
                broadcastConnectionState(state)
            }
        }
    }

    // ─── Broadcasts ───────────────────────────────────────────────────────────

    private fun broadcastConnectionState(state: ConnectionState) {
        val intent = Intent(BleConstants.ACTION_BLE_STATE_CHANGED).apply {
            putExtra(BleConstants.EXTRA_CONNECTION_STATE, state.name)
            putExtra(BleConstants.EXTRA_DEVICE_NAME, bleManager.connectedDeviceName.value)
        }
        sendBroadcast(intent)
    }

    private fun broadcastCommand(command: String) {
        val intent = Intent(BleConstants.ACTION_NAVIGATION_COMMAND).apply {
            putExtra(BleConstants.EXTRA_COMMAND, command)
        }
        sendBroadcast(intent)
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            BleConstants.NOTIFICATION_CHANNEL_ID,
            BleConstants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Wild Tribe Drive BLE connection active"
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String) =
        NotificationCompat.Builder(this, BleConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth_connected)
            .setContentTitle("Wild Tribe Drive")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(dashboardPendingIntent())
            .build()

    private fun updateNotification(state: ConnectionState) {
        val text = when (state) {
            ConnectionState.CONNECTED    -> "Connected to ${bleManager.connectedDeviceName.value ?: "MotoRound"}"
            ConnectionState.CONNECTING   -> "Connecting…"
            ConnectionState.RECONNECTING -> "Reconnecting…"
            ConnectionState.SCANNING     -> "Scanning for devices…"
            ConnectionState.DISCONNECTED -> "Disconnected"
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(BleConstants.FOREGROUND_NOTIFICATION_ID, buildNotification(text))
    }

    private fun dashboardPendingIntent(): PendingIntent {
        val intent = Intent(this, DashboardActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ─── Static Actions ───────────────────────────────────────────────────────

    companion object {
        const val ACTION_CONNECT = "com.wildtribe.drive.ble.CONNECT"
        const val ACTION_DISCONNECT = "com.wildtribe.drive.ble.DISCONNECT"
        const val ACTION_SEND_COMMAND = "com.wildtribe.drive.ble.SEND_COMMAND"

        /** Build a connect intent for [BleService]. */
        fun buildConnectIntent(context: android.content.Context, address: String? = null): Intent =
            Intent(context, BleService::class.java).apply {
                action = ACTION_CONNECT
                address?.let { putExtra(BleConstants.EXTRA_DEVICE_ADDRESS, it) }
            }

        /** Build a send-command intent for [BleService]. */
        fun buildSendIntent(context: android.content.Context, command: String): Intent =
            Intent(context, BleService::class.java).apply {
                action = ACTION_SEND_COMMAND
                putExtra(BleConstants.EXTRA_COMMAND, command)
            }
    }
}
