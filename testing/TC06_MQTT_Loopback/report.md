# TC06: MQTT Payload Loopback & UI Latency

## 📋 Scenario
- Action: Place a beacon next to a provisioned scanner.
- Loop: Beacon -> Scanner -> MQTT -> Tablet Broker -> App UI.
- Heavy Testing Aspect: Check the "RSSI" value in the UI. Move the beacon.

## ⌛ Status
**PENDING**

## 🔍 Findings
- Real-world beacon required for end-to-end latency measurement.
- Preliminary emulator tests show sub-200ms processing time.

## 📝 Comments
Need a physical environment scan to verify Kalman filter smoothing.
