package com.wildtribe.drive.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.wildtribe.drive.ble.BleManager
import com.wildtribe.drive.data.RideRepository
import com.wildtribe.drive.model.BleDevice
import com.wildtribe.drive.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the Bluetooth scan and connection screen.
 */
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RideRepository(application)
    private var _bleManager: BleManager? = null

    val connectionState: StateFlow<ConnectionState>
        get() = _bleManager?.connectionState
            ?: throw IllegalStateException("BleManager not attached")

    val scannedDevices: StateFlow<List<BleDevice>>
        get() = _bleManager?.scannedDevices
            ?: throw IllegalStateException("BleManager not attached")

    val connectedDeviceName: StateFlow<String?>
        get() = _bleManager?.connectedDeviceName
            ?: throw IllegalStateException("BleManager not attached")

    val isManagerAttached: Boolean get() = _bleManager != null

    fun attachBleManager(manager: BleManager) {
        _bleManager = manager
    }

    fun startScan() = _bleManager?.startScan()
    fun stopScan() = _bleManager?.stopScan()

    fun connectToDevice(address: String, name: String) {
        repo.savedDeviceAddress = address
        repo.savedDeviceName = name
        _bleManager?.connect(address)
    }

    fun disconnect() = _bleManager?.disconnect()

    fun hasSavedDevice(): Boolean = repo.savedDeviceAddress != null
    fun getSavedDeviceAddress(): String? = repo.savedDeviceAddress
    fun getSavedDeviceName(): String? = repo.savedDeviceName

    override fun onCleared() {
        super.onCleared()
        _bleManager?.stopScan()
    }
}
