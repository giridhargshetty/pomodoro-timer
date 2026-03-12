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
import com.wildtribe.drive.ble.BleConstants
import com.wildtribe.drive.ble.BleService
import com.wildtribe.drive.databinding.ActivityBluetoothScanBinding
import com.wildtribe.drive.model.BleDevice
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.adapter.DeviceAdapter
import com.wildtribe.drive.ui.dashboard.DashboardActivity
import com.wildtribe.drive.util.LogHelper
import com.wildtribe.drive.util.PermissionHelper
import kotlinx.coroutines.launch

/**
 * Bluetooth scan and connection screen.
 *
 * Flow:
 *  1. Request BLE permissions
 *  2. User taps "SCAN FOR MOTOROUND DEVICE"
 *  3. Devices appear in real-time list
 *  4. User taps "CONNECT" on a device
 *  5. App connects, saves device, navigates to Dashboard
 */
class BluetoothScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBluetoothScanBinding
    private val viewModel: com.wildtribe.drive.viewmodel.BluetoothViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter

    // BleService binding
    private var bleService: BleService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? BleService.LocalBinder ?: return
            bleService = localBinder.getService()
            viewModel.attachBleManager(localBinder.getService().bleManager)
            observeViewModel()
            // Auto-reconnect if a device was saved previously
            if (viewModel.hasSavedDevice()) {
                val savedAddress = viewModel.getSavedDeviceAddress() ?: return
                val savedName = viewModel.getSavedDeviceName() ?: "MotoRound"
                updateStatusBanner("Reconnecting to $savedName…", ConnectionState.RECONNECTING)
                viewModel.connectToDevice(savedAddress, savedName)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            serviceBound = false
        }
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            bindBleService()
        } else {
            showPermissionRationale()
        }
    }

    // Bluetooth state receiver
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> showBluetoothOffBanner()
                BluetoothAdapter.STATE_ON  -> hideBleOffBanner()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        checkPermissionsAndInit()
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device -> onDeviceConnectClicked(device) }
        binding.rvDevices.adapter = deviceAdapter
        binding.rvDevices.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.btnScan.setOnClickListener {
            if (!PermissionHelper.hasBlePermissions(this)) {
                requestBlePermissions()
                return@setOnClickListener
            }
            startScan()
        }

        binding.btnGoToDashboard.setOnClickListener {
            navigateToDashboard()
        }

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    private fun checkPermissionsAndInit() {
        if (PermissionHelper.hasBlePermissions(this)) {
            bindBleService()
        } else {
            requestBlePermissions()
        }
    }

    private fun requestBlePermissions() {
        permissionLauncher.launch(PermissionHelper.blePermissions())
    }

    // ─── Service Binding ──────────────────────────────────────────────────────

    private fun bindBleService() {
        val intent = BleService.buildConnectIntent(this)
        startForegroundService(intent)
        bindService(
            Intent(this, BleService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        serviceBound = true
    }

    // ─── Scan ─────────────────────────────────────────────────────────────────

    private fun startScan() {
        deviceAdapter.submitList(emptyList())
        binding.tvEmptyState.text = getString(R.string.scanning_hint)
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.progressScan.visibility = View.VISIBLE
        binding.btnScan.isEnabled = false
        viewModel.startScan()

        // Re-enable scan button after scan window
        binding.btnScan.postDelayed({
            binding.btnScan.isEnabled = true
            binding.progressScan.visibility = View.GONE
        }, BleConstants.SCAN_PERIOD_MS)
    }

    // ─── ViewModel Observers ──────────────────────────────────────────────────

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
                                // Auto-navigate after 1 second
                                binding.root.postDelayed({ navigateToDashboard() }, 1_000L)
                            }
                            else -> binding.btnGoToDashboard.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    // ─── Device Connection ────────────────────────────────────────────────────

    private fun onDeviceConnectClicked(device: BleDevice) {
        LogHelper.d("BtScanActivity", "User selected: ${device.name} (${device.address})")
        updateStatusBanner("Connecting to ${device.name}…", ConnectionState.CONNECTING)
        viewModel.connectToDevice(device.address, device.name)
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    // ─── UI Helpers ───────────────────────────────────────────────────────────

    private fun updateStatusBanner(text: String, state: ConnectionState) {
        binding.tvConnectionStatus.text = text
        val (dotColor, textColor) = when (state) {
            ConnectionState.CONNECTED    -> Pair(R.color.status_connected, R.color.status_connected)
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING -> Pair(R.color.status_connecting, R.color.status_connecting)
            ConnectionState.SCANNING     -> Pair(R.color.orange_primary, R.color.text_secondary)
            ConnectionState.DISCONNECTED -> Pair(R.color.status_disconnected, R.color.text_secondary)
        }
        binding.viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, dotColor))
        binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, textColor))
    }

    private fun showBluetoothOffBanner() {
        binding.layoutBluetoothOff.visibility = View.VISIBLE
    }

    private fun hideBleOffBanner() {
        binding.layoutBluetoothOff.visibility = View.GONE
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
