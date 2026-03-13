# Wild Tribe Drive – Build Instructions

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Android Studio | Hedgehog (2023.1.1) or newer | Required |
| JDK | 17 | Included with Android Studio |
| Android SDK | API 34 | Install via SDK Manager |
| Gradle | 8.2.2 | Included via wrapper |
| Git | Any | For version control |

---

## 1. Clone / Open the Project

```bash
# If cloning
git clone <repository-url>
cd wild-tribe-drive

# Or open existing project in Android Studio:
# File → Open → Select the project root directory
```

---

## 2. Add the Splash Screen GIF

Place your boot animation file at:

```
app/src/main/res/raw/wildtribe_intro.gif
```

> **Important:** The raw/ directory exists in the project. Simply copy your GIF there.
> If the file is missing, the app shows a fallback logo automatically.

---

## 3. Configure SDK & Sync

In Android Studio:

1. **File → Project Structure → SDK Location**
   - Set Android SDK path (e.g., `/Users/username/Library/Android/sdk`)
2. **File → Sync Project with Gradle Files**
3. Wait for Gradle sync to complete

Or from terminal:
```bash
./gradlew dependencies
```

---

## 4. Build the Project

### Debug APK (for testing)
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK (for distribution)
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release-unsigned.apk`

> **Note:** Release builds require a signing keystore. See Step 6.

### Build All Variants
```bash
./gradlew assemble
```

---

## 5. Install on Device

### Via ADB (USB debugging enabled on device)
```bash
# Install debug build
adb install app/build/outputs/apk/debug/app-debug.apk

# Install and launch
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.wildtribe.drive.debug/.ui.splash.SplashActivity
```

### Via Android Studio
1. Connect device via USB
2. Enable USB Debugging on device: **Settings → Developer Options → USB Debugging**
3. Click **Run** (▶) in Android Studio
4. Select your device from the list

---

## 6. Signing for Release (Production)

### Create a keystore
```bash
keytool -genkey -v \
  -keystore wildtribe_release.jks \
  -alias wildtribe \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

### Configure signing in `app/build.gradle`

Add to the `android {}` block:
```groovy
signingConfigs {
    release {
        storeFile file('wildtribe_release.jks')
        storePassword 'YOUR_STORE_PASSWORD'
        keyAlias 'wildtribe'
        keyPassword 'YOUR_KEY_PASSWORD'
    }
}

buildTypes {
    release {
        signingConfig signingConfigs.release
        // ... rest of config
    }
}
```

> **Security:** Store passwords in `~/.gradle/gradle.properties` instead of in the build file:
> ```
> WILDTRIBE_STORE_PASSWORD=your_password
> WILDTRIBE_KEY_PASSWORD=your_password
> ```

### Build signed release
```bash
./gradlew bundleRelease       # AAB for Play Store
./gradlew assembleRelease     # APK for sideload
```

---

## 7. Testing BLE Functionality

### Physical Device Required
BLE does not work on Android emulators. Use a real Android device.

### Testing with Tasker
1. Install Tasker on the same device
2. Create a task with action: **Code → Send Intent**
3. Set action to `com.wildtribe.drive.SEND_COMMAND`
4. Set extra `command` to any command (e.g., `NAV:R:300m`)
5. Run the task while Wild Tribe Drive is open

### Testing without MotoRound Hardware
You can test the UI and command parsing by sending broadcasts directly:
```bash
adb shell am broadcast \
  -a com.wildtribe.drive.SEND_COMMAND \
  --es command "NAV:R:300m" \
  com.wildtribe.drive.debug
```

Other test commands:
```bash
# Turn left in 500m
adb shell am broadcast -a com.wildtribe.drive.SEND_COMMAND --es command "NAV:L:500m" com.wildtribe.drive.debug

# Speed update 75 km/h
adb shell am broadcast -a com.wildtribe.drive.SEND_COMMAND --es command "SPD:75" com.wildtribe.drive.debug

# Incoming call notification
adb shell am broadcast -a com.wildtribe.drive.SEND_COMMAND --es command "MSG:Ravi calling" com.wildtribe.drive.debug

# Trip info
adb shell am broadcast -a com.wildtribe.drive.SEND_COMMAND --es command "TRP:45km:1h23m:18:30" com.wildtribe.drive.debug

# Arrive at destination
adb shell am broadcast -a com.wildtribe.drive.SEND_COMMAND --es command "NAV:AR:" com.wildtribe.drive.debug

# Clear display
adb shell am broadcast -a com.wildtribe.drive.SEND_COMMAND --es command "CLR" com.wildtribe.drive.debug
```

---

## 8. Enabling Notification Access

For phone notification mirroring, the user must manually grant Notification Access:

1. **Settings → Apps → Special App Access → Notification Access**
2. Enable **Wild Tribe Drive**

Or launch directly:
```bash
adb shell am start -a android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
```

---

## 9. Troubleshooting

### BLE Scan Returns No Devices
- Ensure `ACCESS_FINE_LOCATION` permission is granted
- On Android 12+, ensure `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are granted
- Location services must be enabled on the device (not just permission, but GPS toggle)

### App Crashes on BLE Connect
- Check Android 12+ permission handling in `PermissionHelper.kt`
- Enable debug logs and export via Settings → Export BLE Logs

### Build Fails: SDK Not Found
```bash
echo "sdk.dir=/path/to/android/sdk" > local.properties
```

### Gradle Sync Fails
```bash
./gradlew clean
./gradlew --refresh-dependencies
```

---

## 10. APK Distribution (Sideload)

```bash
# Build release APK
./gradlew assembleRelease

# Transfer to device
adb push app/build/outputs/apk/release/app-release.apk /sdcard/

# Install on device
adb shell pm install -r /sdcard/app-release.apk
```

Or share the APK file directly to the device and open with a file manager.

Enable **Install from Unknown Sources** on the device:
**Settings → Security → Install Unknown Apps**

---

## 11. Build Variants Summary

| Variant | Debug | Release |
|---------|-------|---------|
| App ID | `com.wildtribe.drive.debug` | `com.wildtribe.drive` |
| Logging | Enabled | Disabled (ProGuard) |
| Minification | Off | On |
| Signing | Debug key | Your release keystore |
| Output | `app-debug.apk` | `app-release.apk` |

---

## Support

For bugs or questions:
- Email: support@wildtribedrive.com
- Export BLE debug logs from app Settings → Export Logs
