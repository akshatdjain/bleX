# TC12: Invalid Port & Connection Timeout

## 📋 Scenario
- Action: Manually change the MQTT port to a blocked port (e.g., 9999) in settings and push to a scanner.

## ✅ Status
**DONE**

## 🔍 Findings
- HTTP client correctly times out after 5 seconds.
- Result badge on scanner card shows "HTTP timeout".

## 📝 Comments
Verified error boundaries and exception handling.
