package com.wildtribe.drive.ble

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wildtribe.drive.R
import com.wildtribe.drive.accessibility.MapsAccessibilityService
import com.wildtribe.drive.data.RideRepository
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.ui.dashboard.DashboardActivity
import com.wildtribe.drive.utils.DebugLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * Foreground service owning BleManager and GPS speed tracking.
 *
 * Responsibilities:
 *  - Keeps BLE connection alive in the background
 *  - Receives NAV_UPDATE broadcasts from [MapsAccessibilityService]
 *  - Sends GPS speed every ~1 second, independent of Maps state
 *  - Shows persistent Garmin-green foreground notification
 *
 * TEST: Speed from GPS updates every ~1 second
 */
@SuppressLint("MissingPermission")
class BleService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }
    private val binder = LocalBinder()

    lateinit var bleManager: BleManager
        private set

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repo: RideRepository
    private var locationManager: LocationManager? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(applicationContext)
        repo = RideRepository(applicationContext)
        createNotificationChannel()
        startForeground(BleConstants.FOREGROUND_NOTIFICATION_ID, buildNotification("Disconnected"))
        observeConnectionState()
        registerNavUpdateReceiver()
        startGpsTracking()
        DebugLogger.log("BLE_SVC", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(BleConstants.EXTRA_DEVICE_ADDRESS)
                    ?: repo.savedDeviceAddress
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
        stopGpsTracking()
        unregisterNavUpdateReceiver()
        bleManager.release()
        DebugLogger.log("BLE_SVC", "Service destroyed")
        super.onDestroy()
    }

    // ── Public API ─────────────────────────────────────────────────────────

    fun sendCommandAndBroadcast(command: String) {
        val sent = bleManager.sendCommand(command)
        DebugLogger.log("BLE_SVC", "Send '$command' → success=$sent")
        broadcastCommand(command)
    }

    fun connectToDevice(address: String) {
        repo.savedDeviceAddress = address
        bleManager.connect(address)
    }

    // ── GPS Speed (always-on, independent of Maps) ─────────────────────────

    private val locationListener = LocationListener { location: Location ->
        if (location.hasSpeed()) {
            val kph = (location.speed * 3.6).roundToInt()
            // TEST: Speed from GPS updates every ~1 second
            DebugLogger.log("GPS_SPD", "Speed: ${kph}kph")
            if (bleManager.connectionState.value == ConnectionState.CONNECTED) {
                bleManager.sendCommand(BleConstants.spdCmd(kph))
            }
            broadcastSpeed(kph)
        }
    }

    private fun startGpsTracking() {
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,  // 1 second
                0f,     // any movement
                locationListener
            )
            DebugLogger.log("GPS", "GPS tracking started")
        } catch (e: Exception) {
            DebugLogger.e("GPS", "Failed to start GPS: ${e.message}")
        }
    }

    private fun stopGpsTracking() {
        locationManager?.removeUpdates(locationListener)
        locationManager = null
    }

    // ── Maps Accessibility Receiver ────────────────────────────────────────

    private val navUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != MapsAccessibilityService.ACTION_NAV_UPDATE) return

            val turn     = intent.getStringExtra(MapsAccessibilityService.EXTRA_TURN)
            val distance = intent.getStringExtra(MapsAccessibilityService.EXTRA_DISTANCE)
            val eta      = intent.getStringExtra(MapsAccessibilityService.EXTRA_ETA)
            val remaining= intent.getStringExtra(MapsAccessibilityService.EXTRA_REMAINING)

            // Send NAV command if we have turn data
            if (turn != null) {
                val dist = distance ?: "0"
                val cmd = BleConstants.navCmd(turn, dist)
                if (bleManager.connectionState.value == ConnectionState.CONNECTED) {
                    bleManager.sendCommand(cmd)
                }
                broadcastCommand(cmd)
            }

            // Send TRP command if we have trip data
            if (remaining != null && eta != null) {
                val trpCmd = BleConstants.trpCmd(remaining, "--", eta)
                if (bleManager.connectionState.value == ConnectionState.CONNECTED) {
                    bleManager.sendCommand(trpCmd)
                }
            }
        }
    }

    private fun registerNavUpdateReceiver() {
        val filter = IntentFilter(MapsAccessibilityService.ACTION_NAV_UPDATE)
        registerReceiver(navUpdateReceiver, filter)
    }

    private fun unregisterNavUpdateReceiver() {
        try { unregisterReceiver(navUpdateReceiver) } catch (_: Exception) {}
    }

    // ── Broadcasts ─────────────────────────────────────────────────────────

    private fun observeConnectionState() {
        serviceScope.launch {
            bleManager.connectionState.collectLatest { state ->
                updateNotification(state)
                broadcastConnectionState(state)
            }
        }
    }

    private fun broadcastConnectionState(state: ConnectionState) {
        sendBroadcast(Intent(BleConstants.ACTION_BLE_STATE_CHANGED).apply {
            putExtra(BleConstants.EXTRA_CONNECTION_STATE, state.name)
            putExtra(BleConstants.EXTRA_DEVICE_NAME, bleManager.connectedDeviceName.value)
        })
    }

    private fun broadcastCommand(command: String) {
        sendBroadcast(Intent(BleConstants.ACTION_NAVIGATION_COMMAND).apply {
            putExtra(BleConstants.EXTRA_COMMAND, command)
        })
    }

    private fun broadcastSpeed(kph: Int) {
        sendBroadcast(Intent(ACTION_GPS_SPEED).apply {
            putExtra(EXTRA_SPEED_KPH, kph)
        })
    }

    // ── Notification ───────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            BleConstants.NOTIFICATION_CHANNEL_ID,
            BleConstants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Wild Tribe Drive BLE connection active while riding"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String) =
        NotificationCompat.Builder(this, BleConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth_connected)
            .setContentTitle("Wild Tribe Drive — Riding")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(0x00C896)
            .setContentIntent(dashboardPendingIntent())
            .build()

    private fun updateNotification(state: ConnectionState) {
        val text = when (state) {
            ConnectionState.CONNECTED    -> "Connected to ${bleManager.connectedDeviceName.value ?: "MotoRound"}"
            ConnectionState.CONNECTING   -> "Connecting to MotoRound…"
            ConnectionState.RECONNECTING -> "Reconnecting…"
            ConnectionState.SCANNING     -> "Scanning for devices…"
            ConnectionState.DISCONNECTED -> "Disconnected"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(BleConstants.FOREGROUND_NOTIFICATION_ID, buildNotification(text))
    }

    private fun dashboardPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, DashboardActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val ACTION_CONNECT      = "com.wildtribe.drive.ble.CONNECT"
        const val ACTION_DISCONNECT   = "com.wildtribe.drive.ble.DISCONNECT"
        const val ACTION_SEND_COMMAND = "com.wildtribe.drive.ble.SEND_COMMAND"
        const val ACTION_GPS_SPEED    = "com.wildtribe.drive.GPS_SPEED"
        const val EXTRA_SPEED_KPH     = "speed_kph"

        fun buildConnectIntent(context: Context, address: String? = null): Intent =
            Intent(context, BleService::class.java).apply {
                action = ACTION_CONNECT
                address?.let { putExtra(BleConstants.EXTRA_DEVICE_ADDRESS, it) }
            }

        fun buildSendIntent(context: Context, command: String): Intent =
            Intent(context, BleService::class.java).apply {
                action = ACTION_SEND_COMMAND
                putExtra(BleConstants.EXTRA_COMMAND, command)
            }
    }
}
