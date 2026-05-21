# TC01: Individual MQTT-Only Push (Minimal Payload)

## 📋 Scenario
- Action: On the Scanners tab, find a connected scanner. Tap the "MQTT" action button only.
- Logic: The App sends ONLY `mqtt_host` and `mqtt_port`.
- Practical Expectation: The scanner's `provisioner_service.py` receives the JSON, saves it to `~/mqtt_config.json`, and returns 200 OK. 

## ✅ Status
**DONE**

## 🔍 Findings
- The application correctly isolates the payload.
- The Pi script successfully parses the JSON without requiring SSID/PSK.
- Dynamic broker switching verified without dropping Wi-Fi.

## 📝 Comments
Implemented logic in `ConfiguratorScreen.kt`'s `pushMqttToScanner` and updated the Python script to handle optional fields.

## 📄 Logs
```json
[DEBUG] SENT: {"mqtt_host": "192.168.43.1", "mqtt_port": 1883}
[RESPONSE] 200 OK: {"status": "ok", "message": "Processing config..."}
```
