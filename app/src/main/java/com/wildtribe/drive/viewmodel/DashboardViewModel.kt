package com.wildtribe.drive.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.model.NavCommand
import com.wildtribe.drive.model.NavDirection
import com.wildtribe.drive.model.NavigationData
import com.wildtribe.drive.util.PreferenceHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Dashboard / riding screen.
 *
 * Processes raw BLE commands and exposes clean UI state.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    // ─── Navigation State ─────────────────────────────────────────────────────
    private val _navData = MutableStateFlow(NavigationData())
    val navData: StateFlow<NavigationData> = _navData.asStateFlow()

    // ─── Connection State ─────────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ─── Speed Warning ────────────────────────────────────────────────────────
    private val _speedWarning = MutableStateFlow(false)
    val speedWarning: StateFlow<Boolean> = _speedWarning.asStateFlow()

    // ─── Notification Banner ──────────────────────────────────────────────────
    private val _notification = MutableStateFlow<Pair<String, String>?>(null)
    val notification: StateFlow<Pair<String, String>?> = _notification.asStateFlow()

    // ─── GPS Time ─────────────────────────────────────────────────────────────
    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    // ─── Internals ────────────────────────────────────────────────────────────
    private var notificationDismissJob: Job? = null
    private var timeUpdateJob: Job? = null
    private var navTimeoutJob: Job? = null

    init {
        startClock()
    }

    // ─── Command Processing ───────────────────────────────────────────────────

    /** Process a raw command string received from BLE or Tasker. */
    fun processCommand(raw: String) {
        val command = NavCommand.parse(raw)
        val speedUnit = PreferenceHelper.getSpeedUnit(getApplication())
        val warningLimit = PreferenceHelper.getSpeedWarningLimit(getApplication())
        val now = System.currentTimeMillis()

        resetNavTimeout()

        val current = _navData.value
        _navData.value = when (command) {
            is NavCommand.TurnRight -> current.copy(
                direction = NavDirection.RIGHT,
                distanceText = command.distance,
                instructionText = "TURN RIGHT IN ${command.distance}",
                lastCommandTime = now
            )
            is NavCommand.TurnLeft -> current.copy(
                direction = NavDirection.LEFT,
                distanceText = command.distance,
                instructionText = "TURN LEFT IN ${command.distance}",
                lastCommandTime = now
            )
            is NavCommand.GoStraight -> current.copy(
                direction = NavDirection.STRAIGHT,
                distanceText = command.distance,
                instructionText = if (command.distance.isBlank() || command.distance == "0")
                    "CONTINUE STRAIGHT" else "CONTINUE FOR ${command.distance}",
                lastCommandTime = now
            )
            is NavCommand.UTurn -> current.copy(
                direction = NavDirection.UTURN,
                distanceText = "",
                instructionText = "MAKE A U-TURN",
                lastCommandTime = now
            )
            is NavCommand.Arrive -> current.copy(
                direction = NavDirection.ARRIVE,
                distanceText = "",
                instructionText = "YOU HAVE ARRIVED",
                lastCommandTime = now
            )
            is NavCommand.SpeedUpdate -> {
                val speed = command.speed
                _speedWarning.value = speed > warningLimit
                current.copy(speed = speed, speedUnit = speedUnit, lastCommandTime = now)
            }
            is NavCommand.Message -> {
                showNotificationBanner(command.text, "")
                current
            }
            is NavCommand.TripInfo -> current.copy(
                eta = command.eta,
                distanceRemaining = command.distance,
                tripDuration = command.duration,
                lastCommandTime = now
            )
            is NavCommand.Clear -> NavigationData(lastCommandTime = now)
            is NavCommand.Unknown -> current // Ignore unknown commands silently
        }
    }

    /** Update connection state from broadcast receiver. */
    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    /** Show a phone notification banner for 5 seconds. */
    fun showNotificationBanner(title: String, text: String) {
        _notification.value = Pair(title, text)
        notificationDismissJob?.cancel()
        notificationDismissJob = viewModelScope.launch {
            delay(5_000L)
            _notification.value = null
        }
    }

    // ─── Navigation Timeout ───────────────────────────────────────────────────

    private fun resetNavTimeout() {
        navTimeoutJob?.cancel()
        navTimeoutJob = viewModelScope.launch {
            delay(10_500L) // slightly past the 10s threshold
            val current = _navData.value
            if (current.isWaiting) {
                _navData.value = current.copy(
                    direction = NavDirection.NONE,
                    instructionText = "Waiting for navigation…"
                )
            }
        }
    }

    // ─── Clock ────────────────────────────────────────────────────────────────

    private fun startClock() {
        timeUpdateJob?.cancel()
        timeUpdateJob = viewModelScope.launch {
            while (true) {
                _currentTime.value = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
                delay(30_000L) // Update every 30 seconds
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timeUpdateJob?.cancel()
        navTimeoutJob?.cancel()
        notificationDismissJob?.cancel()
    }
}
