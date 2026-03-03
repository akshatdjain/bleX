# TC09: Tablet Hub Identity Preservation

## 📋 Scenario
- Action: Register "This Tablet" in the Scanners tab. Uninstall the app, reinstall it, and check the "This Tablet" card.

## ✅ Status
**DONE**

## 🔍 Findings
- `ANDROID_ID` remains stable after uninstallation on Android 14.
- MAC address spoofing via hex-ID proved consistent.

## 📝 Comments
Avoids duplicate device registration in the backend.
