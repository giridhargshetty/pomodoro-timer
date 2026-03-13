package com.wildtribe.drive.ui.dashboard

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wildtribe.drive.R
import com.wildtribe.drive.accessibility.MapsAccessibilityService
import com.wildtribe.drive.ble.BleConstants
import com.wildtribe.drive.ble.BleService
import com.wildtribe.drive.data.RideRepository
import com.wildtribe.drive.databinding.ActivityDashboardBinding
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.model.NavDirection
import com.wildtribe.drive.model.NavigationData
import com.wildtribe.drive.ui.settings.SettingsActivity
import com.wildtribe.drive.utils.DebugLogger
import com.wildtribe.drive.utils.PermissionHelper
import com.wildtribe.drive.utils.UnitConverter
import com.wildtribe.drive.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

/**
 * Main Garmin-style riding dashboard.
 *
 * Layout (portrait):
 *  ┌────────────────────────────────┐
 *  │ [BLE●] WILD TRIBE DRIVE [ACC●] │  ← Status bar
 *  ├────────────────────────────────┤
 *  │     [BIG TURN ARROW]           │  ← Nav widget: arrow + direction + distance
 *  ├──────────────┬─────────────────┤
 *  │  SPEED       │  SPEED LIMIT    │  ← Two widgets
 *  ├──────────────┴─────────────────┤
 *  │  ETA          REMAINING        │  ← Two widgets
 *  ├────────────────────────────────┤
 *  │  RIDE TIME    AVG SPEED        │  ← Two widgets
 *  ├────────────────────────────────┤
 *  │ [DISCONNECT] [SETTINGS] [LOG]  │  ← Bottom action bar
 *  └────────────────────────────────┘
 *
 * FIX 7: Screen timeout setting (always_on / 10_min / system)
 *
 * TEST: Speed warning vibrates at correct threshold
 * TEST: Screen stays on during ride (if Always On setting)
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var repo: RideRepository

    private var bleService: BleService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            bleService = (binder as? BleService.LocalBinder)?.getService()
            DebugLogger.log("Dashboard", "Bound to BleService")
        }
        override fun onServiceDisconnected(name: ComponentName?) { bleService = null }
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BleConstants.ACTION_NAVIGATION_COMMAND -> {
                    val cmd = intent.getStringExtra(BleConstants.EXTRA_COMMAND) ?: return
                    viewModel.processCommand(cmd)
                }
                BleConstants.ACTION_BLE_STATE_CHANGED -> {
                    val stateName = intent.getStringExtra(BleConstants.EXTRA_CONNECTION_STATE)
                    val state = stateName?.let {
                        runCatching { ConnectionState.valueOf(it) }.getOrNull()
                    } ?: ConnectionState.DISCONNECTED
                    viewModel.updateConnectionState(state)
                }
                BleService.ACTION_GPS_SPEED -> {
                    val kph = intent.getIntExtra(BleService.EXTRA_SPEED_KPH, 0)
                    viewModel.updateGpsSpeed(kph)
                }
                MapsAccessibilityService.ACTION_SERVICE_STATE -> {
                    val active = intent.getBooleanExtra(
                        MapsAccessibilityService.EXTRA_SERVICE_ACTIVE, false
                    )
                    updateAccIndicator(active)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = RideRepository(this)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FIX 7: Apply screen timeout from settings
        applyScreenTimeout()

        setupClickListeners()
        observeViewModel()
        bindBleService()
        registerCommandReceiver()
        updateAccIndicator(PermissionHelper.isAccessibilityServiceEnabled(this))

        // Show accessibility banner if service not enabled
        val accEnabled = PermissionHelper.isAccessibilityServiceEnabled(this)
        binding.bannerAccess?.visibility = if (accEnabled) View.GONE else View.VISIBLE

        repo.startSession()
        DebugLogger.log("Dashboard", "Activity started")
    }

    override fun onResume() {
        super.onResume()
        // FIX 7: Re-apply screen timeout in case setting changed
        applyScreenTimeout()
        updateAccIndicator(PermissionHelper.isAccessibilityServiceEnabled(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
        if (bleService != null) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
        }
        repo.endSession()
    }

    // ── Screen Timeout (FIX 7) ────────────────────────────────────────────

    /**
     * FIX 7: Control screen wake lock based on settings.
     * TEST: Screen stays on during ride (if Always On setting)
     */
    private fun applyScreenTimeout() {
        when (repo.screenTimeout) {
            "always_on" -> window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else        -> window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Click Listeners ────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnDisconnect?.setOnClickListener {
            bleService?.bleManager?.disconnect()
        }
        binding.btnLog?.setOnClickListener {
            showLogDialog()
        }
        // Tap BLE dot to reconnect
        binding.tvBleIndicator?.setOnClickListener {
            val saved = repo.savedDeviceAddress
            if (bleService?.bleManager?.connectionState?.value != ConnectionState.CONNECTED && saved != null) {
                bleService?.connectToDevice(saved)
            }
        }
        // Tap ACC dot when disabled → open permission flow
        binding.tvAccIndicator?.setOnClickListener {
            if (!PermissionHelper.isAccessibilityServiceEnabled(this)) {
                PermissionHelper.openAccessibilitySettings(this)
            }
        }
        binding.bannerAccess?.setOnClickListener {
            PermissionHelper.openAccessibilitySettings(this)
        }
    }

    // ── Service Binding ────────────────────────────────────────────────────

    private fun bindBleService() {
        startForegroundService(BleService.buildConnectIntent(this))
        bindService(
            Intent(this, BleService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun registerCommandReceiver() {
        val filter = IntentFilter().apply {
            addAction(BleConstants.ACTION_NAVIGATION_COMMAND)
            addAction(BleConstants.ACTION_BLE_STATE_CHANGED)
            addAction(BleService.ACTION_GPS_SPEED)
            addAction(MapsAccessibilityService.ACTION_SERVICE_STATE)
        }
        registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    // ── ViewModel Observers ────────────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.navData.collect { updateNavigationUI(it) } }
                launch { viewModel.connectionState.collect { updateBleIndicator(it) } }
                launch { viewModel.speedWarning.collect { updateSpeedWarning(it) } }
                launch { viewModel.notification.collect { updateNotificationBanner(it) } }
                launch { viewModel.currentTime.collect { binding.tvTime?.text = it } }
                launch { viewModel.rideTime.collect { binding.tvRideTime?.text = it } }
                launch { viewModel.avgSpeed.collect { spd ->
                    binding.tvAvgSpeed?.text = UnitConverter.formatSpeed(spd, repo.useKph)
                }}
            }
        }
    }

    // ── UI Updates ─────────────────────────────────────────────────────────

    private fun updateNavigationUI(data: NavigationData) {
        // Arrow icon + color per direction
        val (arrowRes, arrowColor) = when (data.direction) {
            NavDirection.RIGHT    -> Pair(R.drawable.ic_nav_right,    R.color.nav_right)
            NavDirection.LEFT     -> Pair(R.drawable.ic_nav_left,     R.color.nav_left)
            NavDirection.STRAIGHT -> Pair(R.drawable.ic_nav_straight, R.color.nav_straight)
            NavDirection.UTURN    -> Pair(R.drawable.ic_nav_uturn,    R.color.nav_uturn)
            NavDirection.ARRIVE   -> Pair(R.drawable.ic_nav_arrive,   R.color.nav_arrive)
            NavDirection.NONE     -> Pair(R.drawable.ic_nav_straight, R.color.garmin_gray_2)
        }
        binding.ivNavArrow?.setImageResource(arrowRes)
        binding.ivNavArrow?.alpha = if (data.direction == NavDirection.NONE) 0.3f else 1f

        // U-turn blink animation
        if (data.direction == NavDirection.UTURN) {
            startBlinkAnimation()
        } else {
            stopBlinkAnimation()
        }

        binding.tvNavInstruction?.text = when (data.direction) {
            NavDirection.NONE -> getString(R.string.nav_waiting)
            else              -> data.instructionText
        }
        binding.tvNavDistance?.text = data.distanceText

        // Speed (Roboto Mono, color by warning level)
        val displaySpeed = UnitConverter.formatSpeed(data.speed, repo.useKph)
        binding.tvSpeed?.text = if (data.speed > 0) displaySpeed else "--"
        binding.tvSpeedUnit?.text = UnitConverter.unitLabel(repo.useKph)

        // Speed limit
        binding.tvSpeedLimit?.text = data.speedLimit?.toString() ?: getString(R.string.speed_limit_unknown)

        // Trip widgets
        binding.tvEta?.text = data.eta
        binding.tvRemaining?.text = data.distanceRemaining
    }

    private fun updateBleIndicator(state: ConnectionState) {
        val colorRes = when (state) {
            ConnectionState.CONNECTED    -> R.color.ble_connected
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING -> R.color.ble_scanning
            else                         -> R.color.ble_disconnected
        }
        binding.tvBleIndicator?.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.tvBleLabel?.text = state.name
    }

    private fun updateAccIndicator(active: Boolean) {
        val colorRes = if (active) R.color.acc_active else R.color.acc_disabled
        binding.tvAccIndicator?.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.bannerAccess?.visibility = if (active) View.GONE else View.VISIBLE
    }

    private fun updateSpeedWarning(show: Boolean) {
        binding.layoutSpeedWarning?.visibility = if (show) View.VISIBLE else View.GONE
        val speedColor = when {
            !show -> R.color.speed_normal
            else  -> R.color.speed_danger
        }
        binding.tvSpeed?.setTextColor(ContextCompat.getColor(this, speedColor))

        // TEST: Speed warning vibrates at correct threshold
        if (show && repo.vibrateOnWarning) {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(300L)
        }
    }

    private fun updateNotificationBanner(notification: Pair<String, String>?) {
        if (notification == null) {
            binding.layoutNotification?.visibility = View.GONE
        } else {
            binding.tvNotificationTitle?.text = notification.first
            binding.tvNotificationText?.text = notification.second
            binding.layoutNotification?.visibility = View.VISIBLE
        }
    }

    // ── U-turn blink ───────────────────────────────────────────────────────

    private var blinkRunnable: Runnable? = null

    private fun startBlinkAnimation() {
        stopBlinkAnimation()
        var visible = true
        blinkRunnable = object : Runnable {
            override fun run() {
                binding.ivNavArrow?.alpha = if (visible) 1f else 0.15f
                visible = !visible
                binding.ivNavArrow?.postDelayed(this, 300)
            }
        }
        binding.ivNavArrow?.post(blinkRunnable)
    }

    private fun stopBlinkAnimation() {
        blinkRunnable?.let { binding.ivNavArrow?.removeCallbacks(it) }
        blinkRunnable = null
        binding.ivNavArrow?.alpha = 1f
    }

    // ── Log dialog ─────────────────────────────────────────────────────────

    private fun showLogDialog() {
        val log = com.wildtribe.drive.utils.DebugLogger.getLog()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Debug Log")
            .setMessage(if (log.isEmpty()) "No log entries yet." else log.takeLast(3000))
            .setPositiveButton("Close", null)
            .setNeutralButton("Export") { _, _ -> exportLog() }
            .show()
    }

    private fun exportLog() {
        val file = com.wildtribe.drive.utils.DebugLogger.exportToFile(this) ?: return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "${packageName}.fileprovider", file
        )
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share Debug Log"
        ))
    }
}
