# Wild Tribe Drive

**Wild Tribe Drive** is a production-ready Android BLE navigation companion app for the **MotoRound ESP32-S3** round display device.

The app connects to the MotoRound device via Bluetooth Low Energy (BLE) and displays real-time navigation instructions, speed data, and phone notifications on the rider's device screen.

---

## Features

- **BLE Connection** – Scan, connect, and auto-reconnect to MotoRound devices
- **Garmin-style Dashboard** – Navigation arrow, speed, ETA, and distance widgets
- **Tasker Integration** – Receive navigation commands from Tasker automations
- **Speed Warning** – Visual alert when speed exceeds configured limit
- **Phone Notifications** – Display incoming calls and messages for 5 seconds
- **Auto-Reconnect** – Reconnects every 3 seconds on disconnection
- **Boot Start** – Service restarts after device reboot
- **BLE Debug Logs** – Export logs for troubleshooting
- **Review System** – Prompts after 5 rides or 7 days of use

---

## Project Structure

```
app/src/main/
├── java/com/wildtribe/drive/
│   ├── WildTribeDriveApp.kt          # Application class
│   ├── ble/
│   │   ├── BleConstants.kt           # UUIDs, command strings, intent actions
│   │   ├── BleManager.kt             # Core BLE connection manager
│   │   ├── BleService.kt             # Foreground service (keeps BLE alive)
│   │   └── WildTribeNotificationService.kt  # Phone notification listener
│   ├── ui/
│   │   ├── splash/SplashActivity.kt  # Boot animation → Bluetooth screen
│   │   ├── bluetooth/BluetoothScanActivity.kt  # Scan & connect screen
│   │   ├── dashboard/DashboardActivity.kt      # Main riding dashboard
│   │   └── settings/SettingsActivity.kt        # Settings & preferences
│   ├── viewmodel/
│   │   ├── BluetoothViewModel.kt     # Scan & connection state
│   │   └── DashboardViewModel.kt     # Navigation & speed state
│   ├── model/
│   │   ├── BleDevice.kt              # Scanned device data class
│   │   └── NavigationData.kt         # NavCommand parser & NavDirection
│   ├── adapter/
│   │   └── DeviceAdapter.kt          # RecyclerView adapter for device list
│   ├── receiver/
│   │   ├── TaskerReceiver.kt         # Receives Tasker broadcast commands
│   │   └── BootReceiver.kt           # Auto-starts service after boot
│   └── util/
│       ├── PreferenceHelper.kt       # SharedPreferences wrapper
│       ├── LogHelper.kt              # Debug logging with export
│       ├── PermissionHelper.kt       # BLE permission handling
│       └── ReviewManager.kt         # In-app review logic
└── res/
    ├── layout/                       # XML layouts for all screens
    ├── drawable/                     # Navigation arrows, status icons
    ├── values/                       # Colors, strings, themes, dimens
    ├── raw/                          # wildtribe_intro.gif (add your file here)
    ├── anim/                         # Screen transition animations
    └── xml/                          # Security config, file paths, backup rules
```

---

## BLE Protocol

**Device Name:** `MotoRound`
**Service UUID:** `12345678-1234-1234-1234-123456789abc`
**Characteristic UUID:** `abcdef01-1234-1234-1234-123456789abc`
**Write Mode:** Write without response (UTF-8 encoded)

### Command Reference

| Command | Description | Example |
|---------|-------------|---------|
| `NAV:R:<dist>` | Turn right | `NAV:R:300m` |
| `NAV:L:<dist>` | Turn left | `NAV:L:1.2km` |
| `NAV:S:<dist>` | Continue straight | `NAV:S:0` |
| `NAV:UT:` | Make a U-turn | `NAV:UT:` |
| `NAV:AR:` | Destination arrived | `NAV:AR:` |
| `SPD:<speed>` | Speed update (integer) | `SPD:65` |
| `MSG:<text>` | Display message/notification | `MSG:Ravi calling` |
| `TRP:<dist>:<dur>:<eta>` | Trip info | `TRP:45km:1h23m:18:30` |
| `CLR` | Clear display | `CLR` |

---

## Tasker Integration

Wild Tribe Drive receives navigation commands from Tasker via Android broadcast intents.

### Tasker Action Setup

1. Open Tasker → New Task
2. Add action: **Code → Send Intent**
3. Configure:

```
Action:   com.wildtribe.drive.SEND_COMMAND
Package:  com.wildtribe.drive
Extra:    command:NAV:R:300m
```

### Available Intent Actions

| Intent Action | Purpose |
|--------------|---------|
| `com.wildtribe.drive.SEND_COMMAND` | Send any BLE command |
| `com.wildtribe.drive.NAV_COMMAND` | Navigation-specific command |
| `com.wildtribe.drive.SPEED_UPDATE` | Speed data update |

### Extra Key

`command` → the command string (see Command Reference above)

---

## Permissions Required

| Permission | Purpose |
|-----------|---------|
| `BLUETOOTH_SCAN` | Scan for nearby BLE devices (API 31+) |
| `BLUETOOTH_CONNECT` | Connect to MotoRound device (API 31+) |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Legacy Bluetooth (API < 31) |
| `ACCESS_FINE_LOCATION` | Required for BLE scanning |
| `FOREGROUND_SERVICE` | Keep BLE connection alive in background |
| `POST_NOTIFICATIONS` | Show connection status notification |
| `RECEIVE_BOOT_COMPLETED` | Auto-start after device reboot |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Intercept phone notifications |

---

## Splash Screen GIF

Place your boot animation at:

```
app/src/main/res/raw/wildtribe_intro.gif
```

Requirements:
- Recommended size: 1080×1920 or screen-fill aspect ratio
- Duration: 2–4 seconds (auto-detected)
- Format: GIF (animated)
- Fallback shown automatically if file is missing or corrupted

---

## Architecture

```
SplashActivity
    └── BluetoothScanActivity
            │ (binds to)
            └── BleService (Foreground Service)
                    └── BleManager
                            └── BluetoothGatt → MotoRound Device
            │
            └── DashboardActivity
                    ├── DashboardViewModel
                    ├── BroadcastReceiver ← BleService broadcasts
                    └── BroadcastReceiver ← TaskerReceiver → BleService
```

**Data Flow:**
1. Tasker sends broadcast → `TaskerReceiver` → `BleService` → `BleManager` → GATT write → MotoRound
2. `BleManager` connection changes → `BleService` broadcasts → `DashboardActivity` updates UI
3. Phone notifications → `WildTribeNotificationService` → broadcast → `DashboardActivity` banner

---

## Build & Run

See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) for detailed build steps.

Quick start:
```bash
./gradlew assembleDebug
```

---

## Rider Tips

- Mount phone securely on handlebar using a proper mount
- Disable battery optimization for Wild Tribe Drive in Settings → Apps
- Enable Bluetooth and GPS before starting a ride
- Ensure MotoRound device is fully charged
- Ensure Tasker automation profile is active
- Test BLE connection before long rides
- Increase screen brightness for daytime visibility

---

## Version

**v1.0.0** – Initial production release
**Min SDK:** Android 8.0 (API 26)
**Target SDK:** Android 14 (API 34)
**Language:** Kotlin
**Architecture:** MVVM + Foreground Service

---

## License

Copyright © 2024 Wild Tribe. All rights reserved.
