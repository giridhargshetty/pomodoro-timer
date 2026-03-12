package com.wildtribe.drive.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wildtribe.drive.ble.BleManager
import com.wildtribe.drive.model.BleDevice
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.util.PreferenceHelper
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Bluetooth scan and connection screen.
 *
 * Exposes BLE state as StateFlows so the UI observes reactively.
 * Owns lifecycle of [BleManager] during scanning.
 */
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    // BleManager is injected by the activity after binding to BleService.
    // It is set once the service connection is established.
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

    val isManagerAttached: Boolean
        get() = _bleManager != null

    /** Called when the activity binds to BleService and gets BleManager reference. */
    fun attachBleManager(manager: BleManager) {
        _bleManager = manager
    }

    fun startScan() = _bleManager?.startScan()

    fun stopScan() = _bleManager?.stopScan()

    fun connectToDevice(address: String, name: String) {
        PreferenceHelper.saveDeviceAddress(getApplication(), address)
        PreferenceHelper.saveDeviceName(getApplication(), name)
        _bleManager?.connect(address)
    }

    fun disconnect() = _bleManager?.disconnect()

    fun hasSavedDevice(): Boolean =
        PreferenceHelper.hasSavedDevice(getApplication())

    fun getSavedDeviceAddress(): String? =
        PreferenceHelper.getSavedDeviceAddress(getApplication())

    fun getSavedDeviceName(): String? =
        PreferenceHelper.getSavedDeviceName(getApplication())

    override fun onCleared() {
        super.onCleared()
        _bleManager?.stopScan()
    }
}
