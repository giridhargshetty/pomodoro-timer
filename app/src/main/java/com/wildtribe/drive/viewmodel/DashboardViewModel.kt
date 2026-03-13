package com.wildtribe.drive.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wildtribe.drive.data.RideRepository
import com.wildtribe.drive.model.ConnectionState
import com.wildtribe.drive.model.NavCommand
import com.wildtribe.drive.model.NavDirection
import com.wildtribe.drive.model.NavigationData
import com.wildtribe.drive.utils.DebugLogger
import com.wildtribe.drive.utils.UnitConverter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the Garmin-style Dashboard.
 *
 * Processes raw BLE commands and exposes clean UI state:
 * - Navigation direction, distance, instruction
 * - Speed (from GPS or BLE), warning flag
 * - ETA, remaining distance, ride time, avg speed
 * - Notification banner (5-second auto-dismiss)
 * - Live clock
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RideRepository(application)

    // ── Navigation ─────────────────────────────────────────────────────────
    private val _navData = MutableStateFlow(NavigationData())
    val navData: StateFlow<NavigationData> = _navData.asStateFlow()

    // ── Connection ─────────────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── Speed Warning ──────────────────────────────────────────────────────
    private val _speedWarning = MutableStateFlow(false)
    val speedWarning: StateFlow<Boolean> = _speedWarning.asStateFlow()

    // ── Notification Banner (5-second auto-dismiss) ────────────────────────
    private val _notification = MutableStateFlow<Pair<String, String>?>(null)
    val notification: StateFlow<Pair<String, String>?> = _notification.asStateFlow()

    // ── Live Clock ─────────────────────────────────────────────────────────
    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    // ── Ride Time ──────────────────────────────────────────────────────────
    private val _rideTime = MutableStateFlow("00:00:00")
    val rideTime: StateFlow<String> = _rideTime.asStateFlow()

    // ── Avg Speed ──────────────────────────────────────────────────────────
    private val _avgSpeed = MutableStateFlow(0)
    val avgSpeed: StateFlow<Int> = _avgSpeed.asStateFlow()

    private var notificationDismissJob: Job? = null
    private var navTimeoutJob: Job? = null

    init {
        startClock()
        startRideTimer()
    }

    // ── Command Processing ─────────────────────────────────────────────────

    /**
     * Process raw BLE command string.
     * FIX 4: Unknown commands are logged via NavCommand.parse().
     */
    fun processCommand(raw: String) {
        val command = NavCommand.parse(raw)
        val warningLimit = repo.speedWarningLimit
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
                checkSpeedWarning(speed, warningLimit)
                // Update ride session speed tracking
                repo.currentSession?.updateSpeed(speed)
                _avgSpeed.value = repo.currentSession?.avgSpeedKph ?: 0
                current.copy(speed = speed, speedUnit = if (repo.useKph) "km/h" else "mph", lastCommandTime = now)
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
            is NavCommand.Unknown -> {
                // FIX 4: Already logged in NavCommand.parse()
                current
            }
        }
    }

    /** Update GPS speed from BleService broadcasts. */
    fun updateGpsSpeed(kph: Int) {
        val warningLimit = repo.speedWarningLimit
        checkSpeedWarning(kph, warningLimit)
        repo.currentSession?.updateSpeed(kph)
        _avgSpeed.value = repo.currentSession?.avgSpeedKph ?: 0
        val current = _navData.value
        _navData.value = current.copy(
            speed = kph,
            speedUnit = if (repo.useKph) "km/h" else "mph"
        )
    }

    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    /** Show notification banner for 5 seconds then auto-dismiss. */
    fun showNotificationBanner(title: String, text: String) {
        _notification.value = Pair(title, text)
        notificationDismissJob?.cancel()
        notificationDismissJob = viewModelScope.launch {
            delay(5_000L)
            _notification.value = null
        }
    }

    // ── Speed Warning ──────────────────────────────────────────────────────

    private fun checkSpeedWarning(speedKph: Int, limitKph: Int) {
        _speedWarning.value = speedKph > limitKph
    }

    // ── Navigation Timeout ─────────────────────────────────────────────────

    private fun resetNavTimeout() {
        navTimeoutJob?.cancel()
        navTimeoutJob = viewModelScope.launch {
            delay(10_500L)
            val current = _navData.value
            if (current.isWaiting) {
                _navData.value = current.copy(
                    direction = NavDirection.NONE,
                    instructionText = "WAITING FOR MAPS"
                )
            }
        }
    }

    // ── Clock & Ride Timer ─────────────────────────────────────────────────

    private fun startClock() {
        viewModelScope.launch {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (true) {
                _currentTime.value = fmt.format(Date())
                delay(30_000L)
            }
        }
    }

    private fun startRideTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                _rideTime.value = repo.currentSession?.formattedElapsed() ?: "00:00:00"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        notificationDismissJob?.cancel()
        navTimeoutJob?.cancel()
    }
}
