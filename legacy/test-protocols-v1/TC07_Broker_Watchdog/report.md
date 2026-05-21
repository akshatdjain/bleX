# TC07: Broker Failure & Reconnect Watchdog

## 📋 Scenario
- Action: On the Tablet, toggle "Enable Embedded Broker" to OFF while scanners are active. Wait 1 minute, then toggle ON.

## ✅ Status
**DONE**

## 🔍 Findings
- `connect_mqtt_forever` was verified in scanner code.
- Scanners regained connectivity within 5-10 seconds of broker restart.

## 📝 Comments
Reconnection logic is robust and uses exponential backoff.
