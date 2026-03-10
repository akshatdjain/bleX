# BleGod — Releasing / Shipping the APK

## Building a Shareable APK

Run from the `android/` directory:

```powershell
.\gradlew.bat assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

This APK is **fully signed** with `blegod-release.jks` and can be installed on any Android 12+ device.

---

## Signing Keystore

The release keystore is `android/app/blegod-release.jks`.

| Field         | Value       |
|---------------|-------------|
| Key alias     | blegod      |
| Store password| blegod123   |
| Key password  | blegod123   |
| Validity      | 10,000 days |

> **Important:** Keep `blegod-release.jks` backed up. If you lose it, you can never update the app on devices that already have it installed (you'd need to uninstall first). It is excluded from Git via `.gitignore`.

---

## Installing on a Device (APK sideloading)

### Fresh device (no previous install)

1. **Transfer the APK** using Google Drive, USB, or Telegram (never WhatsApp — it corrupts APKs)
2. Enable **"Install unknown apps"**:
   - Open Settings → Apps → Special app access → Install unknown apps
   - Find the app you're using to open the APK (Files, Chrome, etc.) → Allow
3. **Samsung only:** Settings → Security and privacy → **Auto Blocker → OFF**
4. Tap the APK → Install

### Device with an old version already installed (installed via Android Studio / USB debugging)

1. **Uninstall the existing app** first: Settings → Apps → BleGod → Uninstall
2. Then follow the steps above for a fresh install

> This one-time uninstall is only needed when switching from a "debug" (Android Studio) build to the release APK. Going forward, all release APKs use the same `blegod-release.jks` key and will update directly without uninstalling.

---

## Installing via ADB (USB — bypasses all security checks)

```bash
# Connect device via USB, enable Developer Options + USB Debugging
adb install -r app/build/outputs/apk/release/app-release.apk

# If the device has an incompatible old version:
adb uninstall com.blegod.app
adb install app/build/outputs/apk/release/app-release.apk
```
