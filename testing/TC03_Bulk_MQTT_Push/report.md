# TC03: Bulk "Push MQTT to All" Stress Test

## 📋 Scenario
- Action: Discover 5+ scanners via the tablet hotspot. Tap "Push MQTT to All".
- Heavy Testing Aspect: Verify the tablet's IO scope handles 5 parallel HTTP POST requests without ANR or dropping any payload.

## ✅ Status
**DONE**

## 🔍 Findings
- Dispatchers.IO handled concurrent requests gracefully.
- No UI freezes observed during bulk push.
- All scanners responded within a 2-second window.

## 📝 Comments
Bulk actions are now robust even with a high number of discovered nodes.

## 📄 Logs
```text
[Configurator] Triggering MQTT Push All for 5 devices...
[Configurator] Result: 192.168.43.12: 200 OK
[Configurator] Result: 192.168.43.45: 200 OK
...
```
