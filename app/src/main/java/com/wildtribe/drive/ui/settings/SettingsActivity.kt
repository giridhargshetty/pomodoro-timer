package com.wildtribe.drive.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
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
import com.wildtribe.drive.databinding.ActivitySettingsBinding
import com.wildtribe.drive.util.LogHelper
import com.wildtribe.drive.util.PreferenceHelper
import com.wildtribe.drive.util.ReviewManager

/**
 * Settings screen with:
 *  - Device management (reconnect / forget)
 *  - Speed unit toggle (km/h / mph)
 *  - Speed warning limit slider
 *  - Phone notification toggle
 *  - Tasker setup guide
 *  - Rider tips
 *  - Feedback / log export
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private var bleService: BleService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            bleService = (binder as? BleService.LocalBinder)?.getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) { bleService = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_settings)
        }

        bindBleService()
        loadCurrentSettings()
        setupClickListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bleService != null) unbindService(serviceConnection)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ─── Service ──────────────────────────────────────────────────────────────

    private fun bindBleService() {
        bindService(
            Intent(this, BleService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    // ─── Load Settings ────────────────────────────────────────────────────────

    private fun loadCurrentSettings() {
        // Saved device
        val deviceName = PreferenceHelper.getSavedDeviceName(this)
        val deviceAddress = PreferenceHelper.getSavedDeviceAddress(this)
        binding.tvSavedDevice.text = if (deviceName != null)
            "$deviceName\n$deviceAddress"
        else getString(R.string.no_device_saved)

        // Speed unit
        val unit = PreferenceHelper.getSpeedUnit(this)
        binding.toggleSpeedUnit.check(
            if (unit == "km/h") R.id.btnKmh else R.id.btnMph
        )

        // Speed warning
        val limit = PreferenceHelper.getSpeedWarningLimit(this)
        binding.seekSpeedWarning.progress = limit
        binding.tvSpeedWarningValue.text = "$limit ${PreferenceHelper.getSpeedUnit(this)}"

        // Notifications
        binding.switchNotifications.isChecked = PreferenceHelper.isNotificationsEnabled(this)

        // Ride count
        binding.tvRideCount.text = getString(R.string.rides_completed, PreferenceHelper.getRideCount(this))
    }

    // ─── Click Listeners ──────────────────────────────────────────────────────

    private fun setupClickListeners() {

        // ── Device Management ─────────────────────────────────────────────────

        binding.btnReconnectDevice.setOnClickListener {
            val address = PreferenceHelper.getSavedDeviceAddress(this)
            if (address != null) {
                bleService?.connectToDevice(address)
                    ?: startForegroundService(BleService.buildConnectIntent(this, address))
                toast(getString(R.string.reconnecting))
            } else {
                toast(getString(R.string.no_device_saved))
            }
        }

        binding.btnForgetDevice.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.forget_device_title)
                .setMessage(R.string.forget_device_message)
                .setPositiveButton(R.string.forget) { _, _ ->
                    PreferenceHelper.forgetDevice(this)
                    bleService?.bleManager?.disconnect()
                    binding.tvSavedDevice.text = getString(R.string.no_device_saved)
                    toast(getString(R.string.device_forgotten))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // ── Speed Unit ────────────────────────────────────────────────────────

        binding.toggleSpeedUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val unit = if (checkedId == R.id.btnKmh) "km/h" else "mph"
                PreferenceHelper.saveSpeedUnit(this, unit)
                // Adjust default warning limit when switching units
                if (unit == "mph") {
                    binding.seekSpeedWarning.max = 120
                    binding.seekSpeedWarning.progress = BleConstants.DEFAULT_SPEED_WARNING_MPH
                } else {
                    binding.seekSpeedWarning.max = 200
                    binding.seekSpeedWarning.progress = BleConstants.DEFAULT_SPEED_WARNING_KMH
                }
            }
        }

        // ── Speed Warning Slider ──────────────────────────────────────────────

        binding.seekSpeedWarning.max = 200
        binding.seekSpeedWarning.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val unit = PreferenceHelper.getSpeedUnit(this@SettingsActivity)
                binding.tvSpeedWarningValue.text = "$progress $unit"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                PreferenceHelper.saveSpeedWarningLimit(this@SettingsActivity, seekBar?.progress ?: 100)
            }
        })

        // ── Notifications ─────────────────────────────────────────────────────

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.setNotificationsEnabled(this, isChecked)
            if (isChecked) checkNotificationListenerPermission()
        }

        binding.btnGrantNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // ── Tasker Guide ──────────────────────────────────────────────────────

        binding.btnTaskerGuide.setOnClickListener {
            showTaskerGuideDialog()
        }

        // ── Rider Tips ────────────────────────────────────────────────────────

        binding.btnRiderTips.setOnClickListener {
            showRiderTipsDialog()
        }

        // ── Feedback ──────────────────────────────────────────────────────────

        binding.btnRateApp.setOnClickListener {
            ReviewManager.openStoreListing(this)
        }

        binding.btnSendFeedback.setOnClickListener {
            ReviewManager.sendFeedbackEmail(this)
        }

        binding.btnReportIssue.setOnClickListener {
            ReviewManager.sendFeedbackEmail(this)
        }

        binding.btnExportLogs.setOnClickListener {
            exportLogs()
        }
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private fun showTaskerGuideDialog() {
        val guide = """
TASKER SETUP GUIDE

1. Install Tasker from the Play Store

2. Create a new Task in Tasker

3. Add action: Code → Send Intent

4. Configure:
   Action:  com.wildtribe.drive.SEND_COMMAND
   Package: com.wildtribe.drive
   Extra:   command: NAV:R:300m

5. Trigger the task from your Google Maps profile

EXAMPLE COMMANDS:
• NAV:R:300m   → Turn right in 300m
• NAV:L:1.2km  → Turn left in 1.2km
• NAV:S:0      → Continue straight
• NAV:UT:      → Make a U-turn
• NAV:AR:      → You have arrived
• SPD:65       → Speed update 65 km/h
• TRP:45km:1h23m:18:30 → Trip info
• CLR          → Clear display

For more help visit:
wildtribedrive.com/tasker-setup
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Tasker Integration Guide")
            .setMessage(guide)
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun showRiderTipsDialog() {
        val tips = arrayOf(
            "🏍 Mount phone securely on handlebar",
            "🔋 Disable battery optimization for Wild Tribe Drive",
            "📡 Enable Bluetooth and GPS before riding",
            "⚡ Ensure MotoRound device is fully charged",
            "📱 Ensure Tasker automation is active",
            "☀️ Increase screen brightness for visibility",
            "🔄 Keep the app updated for best performance",
            "🔇 Enable Do Not Disturb during rides",
            "🌐 Test BLE connection before long rides",
            "📶 Keep phone close to MotoRound device"
        )

        AlertDialog.Builder(this)
            .setTitle("Rider Tips")
            .setItems(tips, null)
            .setPositiveButton("Close", null)
            .show()
    }

    // ─── Notification Access ──────────────────────────────────────────────────

    private fun checkNotificationListenerPermission() {
        val listeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: ""
        val hasAccess = listeners.contains(packageName)
        if (!hasAccess) {
            AlertDialog.Builder(this)
                .setTitle("Notification Access Required")
                .setMessage("To show phone notifications on your MotoRound display, Wild Tribe Drive needs Notification Access. Tap Grant to enable it.")
                .setPositiveButton("Grant Access") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    PreferenceHelper.setNotificationsEnabled(this, false)
                    binding.switchNotifications.isChecked = false
                }
                .show()
        }
    }

    // ─── Log Export ───────────────────────────────────────────────────────────

    private fun exportLogs() {
        try {
            val externalDir = getExternalFilesDir(null) ?: cacheDir
            val logFile = LogHelper.writeLogsToFile(externalDir)
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", logFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Wild Tribe Drive Debug Logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share BLE Logs"))
        } catch (e: Exception) {
            LogHelper.e("Settings", "Failed to export logs", e)
            toast("Failed to export logs: ${e.message}")
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
