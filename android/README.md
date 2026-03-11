# BleX Android Controller

The central control unit of the BleX ecosystem. This application manages the fleet of scanners and provides the primary user interface for asset tracking.

## 🌟 Key Components

### 1. `MqttManager`
Handles the integration of the **Moquette** embedded broker. It allows the tablet to receive BLE scan data directly from nodes without needing an external cloud broker.

### 2. `Provisioning System`
Automates the discovery and setup of scanner nodes.
- **UDP Listener**: Listens for heartbeat broadcasts from scanners in "Setup" mode.
- **Provisioning Client**: Pushes WiFi SSID, Password, and the Tablet's IP (as MQTT Host) to the scanner over HTTP.

### 3. `ScanRepository`
The reactive data layer that manages:
- List of nearby beacons.
- List of discovered scanners.
- Status of registered assets and zones.

### 4. `UI Layer` (Jetpack Compose)
- **Scanner View**: Real-time signal strength and asset identification.
- **Configurator**: Zone editing, scanner registration, and mass-provisioning.
- **Settings**: Granular control over MQTT, BLE filters, and payload templates.

## 🛠️ Build Requirements

- **Android Studio Jellyfish** or newer.
- **JDK 17**.
- **Min SDK**: 24 (Android 7.0).

---
*Part of the BleX Suite.*
