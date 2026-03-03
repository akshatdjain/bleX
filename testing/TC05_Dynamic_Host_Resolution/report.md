# TC05: Dynamic Host Resolution (Hotspot Re-entry)

## 📋 Scenario
- Action: Change the tablet's hotspot name or reconnect to a different router (changing Tablet IP). Open the Configurator.
- Check: Verify the "Tablet IP (MQTT Host)" display updates to the new IP.

## ✅ Status
**DONE**

## 🔍 Findings
- `getDeviceIpAddress()` correctly detects network interface changes.
- UI updates in real-time when returning to the Scanners tab.

## 📝 Comments
Critical for users who move between different sites or change their hub connectivity.
