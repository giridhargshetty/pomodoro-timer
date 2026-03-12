package com.wildtribe.drive.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.wildtribe.drive.model.BleDevice
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.util.LogHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * Core BLE manager responsible for:
 *  - Scanning for nearby BLE devices
 *  - Connecting/disconnecting to MotoRound
 *  - Sending commands via GATT write
 *  - Auto-reconnect every 3 seconds on disconnect
 *
 * All public state is exposed as [StateFlow] for reactive UI binding.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    // ─── State Flows ──────────────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    // ─── Internals ────────────────────────────────────────────────────────────
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var lastDeviceAddress: String? = null
    private var shouldAutoReconnect = true
    private val mainHandler = Handler(Looper.getMainLooper())

    // Deduplicated scan results
    private val deviceMap = mutableMapOf<String, BleDevice>()

    // ─── BLE Scanner ──────────────────────────────────────────────────────────

    /** Start scanning for nearby BLE devices. Stops automatically after [BleConstants.SCAN_PERIOD_MS]. */
    fun startScan() {
        if (isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            LogHelper.e("BleManager", "BLE scanner not available")
            return
        }

        deviceMap.clear()
        _scannedDevices.value = emptyList()
        isScanning = true
        _connectionState.value = ConnectionState.SCANNING

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
        LogHelper.d("BleManager", "BLE scan started")

        // Auto-stop after timeout
        mainHandler.postDelayed({
            if (isScanning) stopScan()
        }, BleConstants.SCAN_PERIOD_MS)
    }

    /** Stop any active BLE scan. */
    fun stopScan() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
        LogHelper.d("BleManager", "BLE scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            val address = result.device.address
            val rssi = result.rssi
            val device = BleDevice(name = name, address = address, rssi = rssi)
            deviceMap[address] = device

            // Sort: MotoRound devices first, then by signal strength
            _scannedDevices.value = deviceMap.values.sortedWith(
                compareByDescending<BleDevice> { it.isMotoRound }.thenByDescending { it.rssi }
            )
        }

        override fun onScanFailed(errorCode: Int) {
            LogHelper.e("BleManager", "Scan failed with error: $errorCode")
            isScanning = false
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // ─── Connection ───────────────────────────────────────────────────────────

    /** Connect to a device by its MAC address. */
    fun connect(address: String) {
        stopScan()
        val adapter = bluetoothAdapter ?: return
        val device = adapter.getRemoteDevice(address) ?: return
        lastDeviceAddress = address
        shouldAutoReconnect = true
        _connectionState.value = ConnectionState.CONNECTING
        LogHelper.d("BleManager", "Connecting to $address")
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /** Disconnect from current device and cancel auto-reconnect. */
    fun disconnect() {
        shouldAutoReconnect = false
        mainHandler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        targetCharacteristic = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        LogHelper.d("BleManager", "Disconnected (manual)")
    }

    /** Send a UTF-8 command string to the MotoRound device. */
    fun sendCommand(command: String): Boolean {
        val gatt = bluetoothGatt
        val characteristic = targetCharacteristic
        if (gatt == null || characteristic == null) {
            LogHelper.w("BleManager", "Cannot send – not connected")
            return false
        }

        val bytes = command.toByteArray(StandardCharsets.UTF_8)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeCharacteristic(
                characteristic,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            )
            result == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            characteristic.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    // ─── GATT Callback ────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    LogHelper.d("BleManager", "GATT connected – discovering services")
                    _connectionState.value = ConnectionState.CONNECTING
                    _connectedDeviceName.value = gatt.device?.name
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    targetCharacteristic = null
                    _connectedDeviceName.value = null
                    if (shouldAutoReconnect && lastDeviceAddress != null) {
                        LogHelper.d("BleManager", "Disconnected – scheduling reconnect")
                        _connectionState.value = ConnectionState.RECONNECTING
                        scheduleReconnect()
                    } else {
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                LogHelper.e("BleManager", "Service discovery failed: $status")
                scheduleReconnect()
                return
            }
            val service = gatt.getService(BleConstants.SERVICE_UUID)
            if (service == null) {
                LogHelper.w("BleManager", "MotoRound service not found – wrong device?")
                return
            }
            targetCharacteristic = service.getCharacteristic(BleConstants.CHARACTERISTIC_UUID)
            if (targetCharacteristic != null) {
                _connectionState.value = ConnectionState.CONNECTED
                LogHelper.d("BleManager", "MotoRound service found – ready to send")
            } else {
                LogHelper.e("BleManager", "Characteristic not found in service")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                LogHelper.w("BleManager", "Characteristic write failed: $status")
            }
        }
    }

    // ─── Auto-Reconnect ───────────────────────────────────────────────────────

    private val RECONNECT_TOKEN = Object()

    private fun scheduleReconnect() {
        mainHandler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        mainHandler.postDelayed({
            val address = lastDeviceAddress ?: return@postDelayed
            if (shouldAutoReconnect && _connectionState.value != ConnectionState.CONNECTED) {
                LogHelper.d("BleManager", "Auto-reconnecting to $address")
                connect(address)
            }
        }, BleConstants.RECONNECT_DELAY_MS, RECONNECT_TOKEN)
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    /** Release all BLE resources. Call when the service is destroyed. */
    fun release() {
        shouldAutoReconnect = false
        mainHandler.removeCallbacksAndMessages(null)
        stopScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
        targetCharacteristic = null
    }

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true
}
