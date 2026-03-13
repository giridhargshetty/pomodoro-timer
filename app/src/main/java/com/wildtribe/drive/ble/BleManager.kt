package com.wildtribe.drive.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.wildtribe.drive.model.BleDevice
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.utils.DebugLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * Core BLE manager for MotoRound ESP32-S3 communication.
 *
 * FIX 3: Exponential backoff reconnect (3s → 6s → 12s → ... → 60s max, 10 retries)
 * FIX 6: GATT service discovery timeout (10s — force reconnect if discovery hangs)
 *
 * TEST: BLE connects to device named "MotoRound"
 * TEST: Reconnect backoff: check logs show 3s, 6s, 12s, 24s delays
 * TEST: GATT timeout triggers after 10s if discovery hangs
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    // ── State Flows ────────────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    // ── Internals ──────────────────────────────────────────────────────────
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var lastDeviceAddress: String? = null
    private var shouldAutoReconnect = true
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // FIX 3: Exponential backoff state
    private var reconnectAttempts = 0

    // FIX 6: GATT timeout job
    private var discoveryTimeoutJob: Job? = null

    private val deviceMap = mutableMapOf<String, BleDevice>()

    // ── Scan ───────────────────────────────────────────────────────────────

    fun startScan() {
        if (isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            DebugLogger.e("BleManager", "BLE scanner not available")
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
        DebugLogger.log("BLE_SCAN", "Scan started")

        mainHandler.postDelayed({
            if (isScanning) stopScan()
        }, BleConstants.SCAN_PERIOD_MS)
    }

    fun stopScan() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
        DebugLogger.log("BLE_SCAN", "Scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            val address = result.device.address
            val device = BleDevice(name = name, address = address, rssi = result.rssi)
            deviceMap[address] = device
            _scannedDevices.value = deviceMap.values.sortedWith(
                compareByDescending<BleDevice> { it.isMotoRound }.thenByDescending { it.rssi }
            )
        }
        override fun onScanFailed(errorCode: Int) {
            DebugLogger.e("BLE_SCAN", "Scan failed: $errorCode")
            isScanning = false
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // ── Connect / Disconnect ───────────────────────────────────────────────

    /** Connect to device by MAC address. */
    fun connect(address: String) {
        stopScan()
        val adapter = bluetoothAdapter ?: return
        val device = adapter.getRemoteDevice(address) ?: return
        lastDeviceAddress = address
        shouldAutoReconnect = true
        _connectionState.value = ConnectionState.CONNECTING
        DebugLogger.log("BLE_CONN", "Connecting to $address")
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /** Manual disconnect — cancels auto-reconnect. */
    fun disconnect() {
        shouldAutoReconnect = false
        reconnectAttempts = 0
        discoveryTimeoutJob?.cancel()
        mainHandler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        targetCharacteristic = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        DebugLogger.log("BLE_CONN", "Disconnected (manual)")
    }

    /** Send a UTF-8 command string to MotoRound. Returns true on success. */
    fun sendCommand(command: String): Boolean {
        val gatt = bluetoothGatt
        val characteristic = targetCharacteristic
        if (gatt == null || characteristic == null) {
            DebugLogger.w("BLE_CMD", "Cannot send '$command' — not connected")
            return false
        }
        val bytes = command.toByteArray(StandardCharsets.UTF_8)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeCharacteristic(
                characteristic, bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            )
            (result == BluetoothStatusCodes.SUCCESS).also {
                if (!it) DebugLogger.w("BLE_CMD", "Write failed for '$command': $result")
            }
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            characteristic.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    // ── GATT Callback ──────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    DebugLogger.log("BLE_GATT", "GATT connected — discovering services")
                    _connectionState.value = ConnectionState.CONNECTING
                    _connectedDeviceName.value = gatt.device?.name

                    // FIX 6: Start 10-second service discovery timeout
                    discoveryTimeoutJob?.cancel()
                    discoveryTimeoutJob = scope.launch {
                        delay(BleConstants.GATT_TIMEOUT_MS)
                        DebugLogger.e("BLE_GATT", "Service discovery timed out — forcing reconnect")
                        gatt.disconnect()
                        scheduleReconnect()
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    discoveryTimeoutJob?.cancel()
                    targetCharacteristic = null
                    _connectedDeviceName.value = null
                    if (shouldAutoReconnect && lastDeviceAddress != null) {
                        DebugLogger.log("BLE_GATT", "Disconnected — scheduling reconnect")
                        _connectionState.value = ConnectionState.RECONNECTING
                        scheduleReconnect()
                    } else {
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // FIX 6: Cancel timeout — discovery succeeded
            discoveryTimeoutJob?.cancel()
            discoveryTimeoutJob = null

            if (status != BluetoothGatt.GATT_SUCCESS) {
                DebugLogger.e("BLE_GATT", "Service discovery failed: $status")
                scheduleReconnect()
                return
            }
            val service = gatt.getService(BleConstants.SERVICE_UUID)
            if (service == null) {
                DebugLogger.w("BLE_GATT", "MotoRound service UUID not found")
                return
            }
            targetCharacteristic = service.getCharacteristic(BleConstants.CHARACTERISTIC_UUID)
            if (targetCharacteristic != null) {
                onConnected()
            } else {
                DebugLogger.e("BLE_GATT", "Characteristic not found in service")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                DebugLogger.w("BLE_GATT", "Characteristic write failed: $status")
            }
        }
    }

    // ── Connection success ─────────────────────────────────────────────────

    private fun onConnected() {
        // FIX 3: Reset reconnect counter on success
        reconnectAttempts = 0
        _connectionState.value = ConnectionState.CONNECTED
        DebugLogger.log("BLE_CONN", "MotoRound connected and ready")
    }

    // ── Exponential Backoff Reconnect ──────────────────────────────────────

    private val RECONNECT_TOKEN = Object()

    /**
     * FIX 3: Exponential backoff reconnect.
     * Delays: 3s, 6s, 12s, 24s, 48s, 60s, 60s... (capped at MAX_RECONNECT_MS)
     * Stops after MAX_RECONNECT_TRIES attempts.
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= BleConstants.MAX_RECONNECT_TRIES) {
            DebugLogger.log("BLE_RECON", "Max reconnect attempts ($reconnectAttempts) reached — giving up")
            _connectionState.value = ConnectionState.DISCONNECTED
            reconnectAttempts = 0
            return
        }

        val delay = minOf(
            BleConstants.BASE_RECONNECT_MS * (1L shl reconnectAttempts),
            BleConstants.MAX_RECONNECT_MS
        )
        reconnectAttempts++
        DebugLogger.log("BLE_RECON", "Attempt $reconnectAttempts — waiting ${delay}ms")

        mainHandler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        mainHandler.postAtTime({
            val address = lastDeviceAddress ?: return@postAtTime
            if (shouldAutoReconnect && _connectionState.value != ConnectionState.CONNECTED) {
                connect(address)
            }
        }, RECONNECT_TOKEN, android.os.SystemClock.uptimeMillis() + delay)
    }

    // ── Cleanup ────────────────────────────────────────────────────────────

    fun release() {
        shouldAutoReconnect = false
        reconnectAttempts = 0
        discoveryTimeoutJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        scope.cancel()
        stopScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
        targetCharacteristic = null
    }

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true
}
