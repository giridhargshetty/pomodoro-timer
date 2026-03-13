package com.wildtribe.drive.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.wildtribe.drive.R
import com.wildtribe.drive.ble.BleConstants
import com.wildtribe.drive.ble.BleService
import com.wildtribe.drive.data.RideRepository
import com.wildtribe.drive.databinding.ActivitySettingsBinding
import com.wildtribe.drive.utils.DebugLogger
import com.wildtribe.drive.utils.PermissionHelper
import com.wildtribe.drive.utils.UnitConverter
import com.wildtribe.drive.util.ReviewManager

/**
 * Garmin-style Settings screen.
 *
 * Sections: DEVICE | UNITS | WARNINGS | ACCESSIBILITY | DISPLAY | NOTIFICATIONS | DEBUG | ABOUT
 *
 * FIX 2: Auto-converts stored speed warning limit when unit changes
 * FIX 7: Screen timeout setting persisted for Dashboard
 *
 * TEST: Unit conversion: 100 km/h warning → 62 mph after unit switch
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repo: RideRepository

    private var bleService: BleService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            bleService = (binder as? BleService.LocalBinder)?.getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) { bleService = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = RideRepository(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_settings)
        }

        bindService(Intent(this, BleService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        loadCurrentSettings()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bleService != null) try { unbindService(serviceConnection) } catch (_: Exception) {}
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Load Settings ──────────────────────────────────────────────────────

    private fun loadCurrentSettings() {
        // ── DEVICE section ────────────────────────────────────────────────
        val deviceName = repo.savedDeviceName
        val deviceAddress = repo.savedDeviceAddress
        binding.tvSavedDevice.text = if (deviceName != null)
            "$deviceName\n$deviceAddress"
        else getString(R.string.no_device_saved)

        // ── UNITS section ─────────────────────────────────────────────────
        binding.toggleSpeedUnit?.check(
            if (repo.useKph) R.id.btnKmh else R.id.btnMph
        )

        // ── WARNINGS section ──────────────────────────────────────────────
        val limit = repo.speedWarningLimit
        binding.seekSpeedWarning?.progress = limit
        binding.tvSpeedWarningValue?.text = "$limit ${if (repo.useKph) "km/h" else "mph"}"

        // ── ACCESSIBILITY section ──────────────────────────────────────────
        updateAccessibilityStatus()

        // ── DISPLAY section ───────────────────────────────────────────────
        // Screen timeout radio/toggle (if view exists)

        // ── NOTIFICATIONS section ─────────────────────────────────────────
        binding.switchNotifications?.isChecked = repo.notificationsEnabled

        // ── ABOUT section ─────────────────────────────────────────────────
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            binding.tvVersionName?.text = versionName
        } catch (_: Exception) {}
        binding.tvRideCount?.text = getString(R.string.rides_completed, repo.rideCount)
    }

    private fun updateAccessibilityStatus() {
        val enabled = PermissionHelper.isAccessibilityServiceEnabled(this)
        binding.tvAccStatus?.text = if (enabled)
            getString(R.string.acc_status_active)
        else getString(R.string.acc_status_inactive)
    }

    // ── Click Listeners ────────────────────────────────────────────────────

    private fun setupClickListeners() {

        // ── DEVICE ────────────────────────────────────────────────────────
        binding.btnReconnectDevice.setOnClickListener {
            val address = repo.savedDeviceAddress
            if (address != null) {
                bleService?.connectToDevice(address)
                    ?: startForegroundService(BleService.buildConnectIntent(this, address))
                toast(getString(R.string.reconnecting))
            } else toast(getString(R.string.no_device_saved))
        }

        binding.btnForgetDevice.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.forget_device_title)
                .setMessage(R.string.forget_device_message)
                .setPositiveButton(R.string.forget) { _, _ ->
                    repo.forgetDevice()
                    bleService?.bleManager?.disconnect()
                    binding.tvSavedDevice.text = getString(R.string.no_device_saved)
                    toast(getString(R.string.device_forgotten))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // ── UNITS (FIX 2) ─────────────────────────────────────────────────
        binding.toggleSpeedUnit?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val switchingToKph = checkedId == R.id.btnKmh
            val currentLimit = repo.speedWarningLimit

            // FIX 2: Auto-convert stored warning limit when unit changes
            val convertedLimit = UnitConverter.convertWarningLimit(
                currentLimit,
                switchingToKph = switchingToKph
            )
            repo.useKph = switchingToKph
            repo.speedWarningLimit = convertedLimit

            // Update slider to reflect converted value
            binding.seekSpeedWarning?.max = if (switchingToKph) 200 else 120
            binding.seekSpeedWarning?.progress = convertedLimit
            binding.tvSpeedWarningValue?.text = "$convertedLimit ${if (switchingToKph) "km/h" else "mph"}"
            DebugLogger.log("SETTINGS", "Unit→${if (switchingToKph) "kph" else "mph"} limit=$convertedLimit")
        }

        // ── WARNINGS ──────────────────────────────────────────────────────
        binding.seekSpeedWarning?.max = if (repo.useKph) 200 else 120
        binding.seekSpeedWarning?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val unit = if (repo.useKph) "km/h" else "mph"
                binding.tvSpeedWarningValue?.text = "$progress $unit"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                repo.speedWarningLimit = seekBar?.progress ?: 100
            }
        })

        binding.switchVibrate?.setOnCheckedChangeListener { _, checked ->
            repo.vibrateOnWarning = checked
        }

        // ── ACCESSIBILITY ─────────────────────────────────────────────────
        binding.btnEnableAcc?.setOnClickListener {
            if (PermissionHelper.isAccessibilityServiceEnabled(this)) {
                PermissionHelper.openAccessibilitySettings(this)
            } else {
                showAccessibilityDialog()
            }
        }

        // ── DISPLAY (FIX 7) ───────────────────────────────────────────────
        binding.btnScreenAlwaysOn?.setOnClickListener {
            repo.screenTimeout = "always_on"
            toast("Screen: Always On")
        }
        binding.btnScreen10Min?.setOnClickListener {
            repo.screenTimeout = "10_min"
            toast("Screen: 10 Minutes")
        }
        binding.btnScreenSystem?.setOnClickListener {
            repo.screenTimeout = "system"
            toast("Screen: System Default")
        }

        // ── NOTIFICATIONS ─────────────────────────────────────────────────
        binding.switchNotifications?.setOnCheckedChangeListener { _, isChecked ->
            repo.notificationsEnabled = isChecked
            if (isChecked && !PermissionHelper.isNotificationListenerEnabled(this)) {
                showNotificationPermissionDialog()
            }
        }
        binding.btnGrantNotifAccess?.setOnClickListener {
            PermissionHelper.openNotificationListenerSettings(this)
        }

        // ── DEBUG ─────────────────────────────────────────────────────────
        binding.btnExportLogs?.setOnClickListener { exportLogs() }
        binding.btnClearLogs?.setOnClickListener {
            DebugLogger.clear()
            toast("Log cleared")
        }
        binding.btnViewLogs?.setOnClickListener {
            val log = DebugLogger.getLog()
            AlertDialog.Builder(this)
                .setTitle("Debug Log")
                .setMessage(if (log.isEmpty()) "No entries." else log.takeLast(3000))
                .setPositiveButton("Close", null)
                .show()
        }

        // ── ABOUT ─────────────────────────────────────────────────────────
        binding.btnRateApp?.setOnClickListener { ReviewManager.openStoreListing(this) }
        binding.btnTaskerGuide?.setOnClickListener { showTaskerGuideDialog() }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.acc_dialog_title))
            .setMessage(getString(R.string.acc_dialog_message))
            .setPositiveButton(getString(R.string.acc_dialog_btn_open)) { _, _ ->
                PermissionHelper.openAccessibilitySettings(this)
            }
            .setNegativeButton(getString(R.string.acc_dialog_btn_skip), null)
            .show()
    }

    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Access Required")
            .setMessage("To show phone notifications on your MotoRound display, Wild Tribe Drive needs Notification Access.")
            .setPositiveButton("Grant Access") { _, _ ->
                PermissionHelper.openNotificationListenerSettings(this)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                repo.notificationsEnabled = false
                binding.switchNotifications?.isChecked = false
            }
            .show()
    }

    private fun showTaskerGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle("Tasker Integration Guide")
            .setMessage("""
TASKER SETUP GUIDE

1. Install Tasker from the Play Store

2. Create a new Task in Tasker

3. Add action: Code → Send Intent

4. Configure:
   Action:  com.wildtribe.drive.SEND_COMMAND
   Extra:   command: NAV:R:300m

EXAMPLE COMMANDS:
• NAV:R:300m   → Turn right in 300m
• NAV:L:1.2km  → Turn left in 1.2km
• NAV:S:0      → Continue straight
• NAV:UT:      → Make a U-turn
• NAV:AR:      → You have arrived
• SPD:65       → Speed update 65 km/h
• TRP:45km:1h23m:18:30 → Trip info
• CLR          → Clear display
            """.trimIndent())
            .setPositiveButton("Got it", null)
            .show()
    }

    // ── Log Export ─────────────────────────────────────────────────────────

    private fun exportLogs() {
        val file = DebugLogger.exportToFile(this) ?: run {
            toast("Failed to export log")
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Wild Tribe Drive Debug Log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share Debug Log"
        ))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
