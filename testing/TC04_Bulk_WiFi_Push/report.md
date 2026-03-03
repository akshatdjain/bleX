# TC04: Bulk "Push Wi-Fi to All" Network Transition

## 📋 Scenario
- Action: With multiple scanners discovered, tap "Push Wi-Fi to All".
- Heavy Testing Aspect: This triggers multiple scanners to leave the 'setup' AP at once.

## ✅ Status
**DONE**

## 🔍 Findings
- Tablet hotspot remained stable.
- scanners successfully migrated to the target SSID.

## 📝 Comments
Verified the priority system (`connection.autoconnect-priority 10`) ensures the scanner doesn't jump back to 'setup' accidentally.
