# Scanner Provisioning Package

Scripts and services for configuring edge scanner nodes (Raspberry Pi & ESP32).

## 🧩 Provisioning Flow

1. **Setup Mode**: The scanner boots and connects to the `AsseTrack-Setup` hotspot (or creates its own).
2. **Discovery**: It broadcasts a UDP heartbeat on port 9000.
3. **Provisioning**: The Android Hub detects the heartbeat and sends a POST request to the scanner's `Provisioner Service` (port 8888).
4. **Configuration**: The scanner saves the WiFi credentials and MQTT broker IP, then reboots into "Active Mode".

## 📦 Contents

### `/pi`
- `provisioner_service.py`: Lightweight HTTP server that listens for setup commands.
- `discovery_broadcast.py`: UDP beacon script.
- `assetrack-discovery.service`: Systemd unit file for auto-start.

### `/esp32`
- Firmware templates for ESP32/ESP8266 devices.

---
*Enable seamless scaling of your tracking network.*
