package com.wildtribe.drive.ui.dashboard

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wildtribe.drive.R
import com.wildtribe.drive.ble.BleConstants
import com.wildtribe.drive.ble.BleService
import com.wildtribe.drive.databinding.ActivityDashboardBinding
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.model.NavDirection
import com.wildtribe.drive.ui.settings.SettingsActivity
import com.wildtribe.drive.util.LogHelper
import com.wildtribe.drive.util.PreferenceHelper
import com.wildtribe.drive.util.ReviewManager
import com.wildtribe.drive.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

/**
 * Main riding dashboard – the Garmin-style navigation companion screen.
 *
 * Layout zones:
 *  - Top-left:   BLE connection status indicator
 *  - Top-center: GPS time (HH:mm)
 *  - Center:     Large navigation arrow
 *  - Below arrow: Instruction text
 *  - Bottom:      Speed | ETA | Distance widgets
 *  - Overlay:     Speed warning banner
 *  - Overlay:     Incoming notification banner (5s auto-dismiss)
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()

    // ─── BLE Service Binding ──────────────────────────────────────────────────
    private var bleService: BleService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            bleService = (binder as? BleService.LocalBinder)?.getService()
            LogHelper.d("Dashboard", "Bound to BleService")
        }
        override fun onServiceDisconnected(name: ComponentName?) { bleService = null }
    }

    // ─── Broadcast Receiver ───────────────────────────────────────────────────
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BleConstants.ACTION_NAVIGATION_COMMAND -> {
                    val cmd = intent.getStringExtra(BleConstants.EXTRA_COMMAND) ?: return
                    LogHelper.d("Dashboard", "Nav command: $cmd")
                    viewModel.processCommand(cmd)
                }
                BleConstants.ACTION_BLE_STATE_CHANGED -> {
                    val stateName = intent.getStringExtra(BleConstants.EXTRA_CONNECTION_STATE)
                    val state = stateName?.let { runCatching { ConnectionState.valueOf(it) }.getOrNull() }
                        ?: ConnectionState.DISCONNECTED
                    viewModel.updateConnectionState(state)
                }
                BleConstants.ACTION_PHONE_NOTIFICATION -> {
                    val title = intent.getStringExtra(BleConstants.EXTRA_NOTIFICATION_TITLE) ?: return
                    val text = intent.getStringExtra(BleConstants.EXTRA_NOTIFICATION_TEXT) ?: ""
                    viewModel.showNotificationBanner(title, text)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while riding
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
        bindBleService()
        registerCommandReceiver()

        // Track ride start for review eligibility
        PreferenceHelper.incrementRideCount(this)
    }

    override fun onResume() {
        super.onResume()
        // Check review eligibility after rides threshold
        ReviewManager.checkAndRequestReview(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(commandReceiver)
        if (bleService != null) {
            unbindService(serviceConnection)
        }
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnBleStatus.setOnClickListener {
            // Tap BLE indicator to reconnect
            if (bleService?.bleManager?.connectionState?.value != ConnectionState.CONNECTED) {
                val savedAddress = PreferenceHelper.getSavedDeviceAddress(this)
                savedAddress?.let { bleService?.connectToDevice(it) }
            }
        }
    }

    private fun bindBleService() {
        val intent = BleService.buildConnectIntent(this)
        startForegroundService(intent)
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
            addAction(BleConstants.ACTION_PHONE_NOTIFICATION)
        }
        registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    // ─── ViewModel Observers ──────────────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.navData.collect { data -> updateNavigationUI(data) } }
                launch { viewModel.connectionState.collect { state -> updateBleIndicator(state) } }
                launch { viewModel.speedWarning.collect { warn -> updateSpeedWarning(warn) } }
                launch { viewModel.notification.collect { notif -> updateNotificationBanner(notif) } }
                launch { viewModel.currentTime.collect { time -> binding.tvTime.text = time } }
            }
        }
    }

    // ─── UI Update Methods ────────────────────────────────────────────────────

    private fun updateNavigationUI(data: com.wildtribe.drive.model.NavigationData) {
        // Navigation arrow
        val arrowRes = when (data.direction) {
            NavDirection.RIGHT    -> R.drawable.ic_arrow_right
            NavDirection.LEFT     -> R.drawable.ic_arrow_left
            NavDirection.STRAIGHT -> R.drawable.ic_arrow_straight
            NavDirection.UTURN    -> R.drawable.ic_arrow_uturn
            NavDirection.ARRIVE   -> R.drawable.ic_arrive
            NavDirection.NONE     -> R.drawable.ic_arrow_straight
        }
        binding.ivNavArrow.setImageResource(arrowRes)

        // Show/hide arrow (hide when no active navigation)
        binding.ivNavArrow.alpha = if (data.direction == NavDirection.NONE) 0.3f else 1f

        // Instruction text
        binding.tvNavInstruction.text = data.instructionText

        // Speed widget
        val speedText = if (data.speed > 0) "${data.speed}" else "--"
        binding.tvSpeed.text = speedText
        binding.tvSpeedUnit.text = data.speedUnit

        // Bottom widgets
        binding.tvEta.text = data.eta
        binding.tvDistanceRemaining.text = data.distanceRemaining
    }

    private fun updateBleIndicator(state: ConnectionState) {
        val (colorRes, label) = when (state) {
            ConnectionState.CONNECTED    -> Pair(R.color.status_connected, "●")
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING -> Pair(R.color.status_connecting, "●")
            ConnectionState.SCANNING,
            ConnectionState.DISCONNECTED -> Pair(R.color.status_disconnected, "●")
        }
        binding.tvBleIndicator.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.tvBleIndicator.text = label
        binding.tvBleLabel.text = state.displayName
    }

    private fun updateSpeedWarning(show: Boolean) {
        binding.layoutSpeedWarning.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.tvSpeed.setTextColor(ContextCompat.getColor(this, R.color.warning_red))
        } else {
            binding.tvSpeed.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun updateNotificationBanner(notification: Pair<String, String>?) {
        if (notification == null) {
            binding.layoutNotification.visibility = View.GONE
        } else {
            binding.tvNotificationTitle.text = notification.first
            binding.tvNotificationText.text = notification.second
            binding.layoutNotification.visibility = View.VISIBLE
        }
    }
}
