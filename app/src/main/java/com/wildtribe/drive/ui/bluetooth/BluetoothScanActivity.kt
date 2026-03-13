package com.wildtribe.drive.ui.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wildtribe.drive.R
import com.wildtribe.drive.adapter.DeviceAdapter
import com.wildtribe.drive.ble.BleConstants
import com.wildtribe.drive.ble.BleService
import com.wildtribe.drive.data.RideRepository
import com.wildtribe.drive.databinding.ActivityBluetoothScanBinding
import com.wildtribe.drive.model.BleDevice
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.ui.dashboard.DashboardActivity
import com.wildtribe.drive.utils.DebugLogger
import com.wildtribe.drive.utils.PermissionHelper
import com.wildtribe.drive.viewmodel.BluetoothViewModel
import kotlinx.coroutines.launch

/**
 * Garmin-style Bluetooth scan and connection screen.
 *
 * Features:
 * - Animated scanning pulse (concentric circles)
 * - MotoRound devices highlighted with green left border
 * - "Enable Maps Reading" button if Accessibility Service not granted
 * - Permission dialog explaining WHY before opening settings
 *
 * TEST: BLE connects to device named "MotoRound"
 * TEST: Accessibility service starts after permission granted
 */
class BluetoothScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBluetoothScanBinding
    private val viewModel: BluetoothViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var repo: RideRepository

    private var bleService: BleService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? BleService.LocalBinder ?: return
            bleService = localBinder.getService()
            viewModel.attachBleManager(localBinder.getService().bleManager)
            observeViewModel()
            // Auto-reconnect if a device was previously saved
            if (viewModel.hasSavedDevice()) {
                val address = viewModel.getSavedDeviceAddress() ?: return
                val name2 = viewModel.getSavedDeviceName() ?: "MotoRound"
                updateStatusBanner("Reconnecting to $name2…", ConnectionState.RECONNECTING)
                viewModel.connectToDevice(address, name2)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            serviceBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) bindBleService()
        else showPermissionRationale()
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> binding.layoutBluetoothOff.visibility = View.VISIBLE
                BluetoothAdapter.STATE_ON  -> binding.layoutBluetoothOff.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = RideRepository(this)
        binding = ActivityBluetoothScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        checkPermissionsAndInit()
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        // Show accessibility button if not yet granted
        updateAccessibilityButton()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(bluetoothStateReceiver) } catch (_: Exception) {}
        if (serviceBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
            serviceBound = false
        }
    }

    // ── Setup ──────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device -> onDeviceConnectClicked(device) }
        binding.rvDevices.adapter = deviceAdapter
        binding.rvDevices.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.btnScan.setOnClickListener {
            if (!PermissionHelper.hasBlePermissions(this)) {
                requestBlePermissions(); return@setOnClickListener
            }
            startScan()
        }
        binding.btnGoToDashboard.setOnClickListener { navigateToDashboard() }
        binding.btnOpenSettings?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        binding.btnEnableAccessibility?.setOnClickListener {
            showAccessibilityPermissionDialog()
        }
    }

    private fun checkPermissionsAndInit() {
        if (PermissionHelper.hasBlePermissions(this)) bindBleService()
        else requestBlePermissions()
    }

    private fun requestBlePermissions() {
        permissionLauncher.launch(PermissionHelper.blePermissions())
    }

    private fun updateAccessibilityButton() {
        val enabled = PermissionHelper.isAccessibilityServiceEnabled(this)
        binding.btnEnableAccessibility?.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    // ── Accessibility Permission Dialog ────────────────────────────────────

    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.acc_dialog_title))
            .setMessage(getString(R.string.acc_dialog_message))
            .setPositiveButton(getString(R.string.acc_dialog_btn_open)) { _, _ ->
                PermissionHelper.openAccessibilitySettings(this)
            }
            .setNegativeButton(getString(R.string.acc_dialog_btn_skip), null)
            .show()
    }

    // ── Service Binding ────────────────────────────────────────────────────

    private fun bindBleService() {
        startForegroundService(BleService.buildConnectIntent(this))
        bindService(Intent(this, BleService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        serviceBound = true
    }

    // ── Scan ───────────────────────────────────────────────────────────────

    private fun startScan() {
        deviceAdapter.submitList(emptyList())
        binding.tvEmptyState.text = getString(R.string.scanning_hint)
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.btnScan.isEnabled = false
        viewModel.startScan()

        binding.btnScan.postDelayed({
            binding.btnScan.isEnabled = true
        }, BleConstants.SCAN_PERIOD_MS)
    }

    // ── ViewModel Observers ────────────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.scannedDevices.collect { devices ->
                        deviceAdapter.submitList(devices)
                        binding.tvEmptyState.visibility =
                            if (devices.isEmpty()) View.VISIBLE else View.GONE
                        if (devices.isEmpty()) {
                            binding.tvEmptyState.text = getString(R.string.no_devices_found)
                        }
                    }
                }
                launch {
                    viewModel.connectionState.collect { state ->
                        updateStatusBanner(state.displayName, state)
                        when (state) {
                            ConnectionState.CONNECTED -> {
                                binding.btnGoToDashboard.visibility = View.VISIBLE
                                binding.root.postDelayed({ navigateToDashboard() }, 1_000L)
                            }
                            else -> binding.btnGoToDashboard.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    // ── Device Connection ──────────────────────────────────────────────────

    private fun onDeviceConnectClicked(device: BleDevice) {
        DebugLogger.log("BtScan", "User selected: ${device.name} (${device.address})")
        updateStatusBanner("Connecting to ${device.name}…", ConnectionState.CONNECTING)
        viewModel.connectToDevice(device.address, device.name)
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    // ── UI Helpers ─────────────────────────────────────────────────────────

    private fun updateStatusBanner(text: String, state: ConnectionState) {
        binding.tvConnectionStatus.text = text
        val colorRes = when (state) {
            ConnectionState.CONNECTED    -> R.color.ble_connected
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING -> R.color.ble_scanning
            ConnectionState.SCANNING     -> R.color.garmin_amber
            ConnectionState.DISCONNECTED -> R.color.ble_disconnected
        }
        binding.viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, colorRes))
        binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.permission_bluetooth_rationale)
            .setPositiveButton(R.string.grant_permission) { _, _ -> requestBlePermissions() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
