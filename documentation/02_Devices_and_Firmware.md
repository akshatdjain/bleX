# BleX IoT Technical Architecture - Devices and Firmware

**Document Version:** 1.0  
**Last Updated:** 16th of April 2026  
**Status:** Production  
**Classification:** Internal

---

## Table of Contents
1. [Overview](#overview)
2. [BLE Beacon Devices](#ble-beacon-devices)
3. [Edge Scanner Devices](#edge-scanner-devices)
4. [Android Tablet Hub](#android-tablet-hub)
5. [Firmware Architecture](#firmware-architecture)
6. [Device Lifecycle Management](#device-lifecycle-management)
7. [Performance Specifications](#performance-specifications)

---

## Overview

The BleX system consists of three device tiers:
1. **BLE Beacons** - Passive broadcasting devices attached to assets
2. **Edge Scanners** - Active scanning devices (Raspberry Pi, ESP32)
3. **Android Tablet Hub** - Central orchestration and processing hub

This document provides detailed specifications for each device type, firmware architecture, and operational characteristics.

---

## BLE Beacon Devices

### Beacon Standards

#### iBeacon (Apple)
**Technical Specification:**
- **Company ID**: 0x004C (Apple Inc.)
- **Beacon Type**: 0x02 (iBeacon)
- **Data Length**: 0x15 (21 bytes)
- **Payload Structure**:
  ```
  [Company ID: 2 bytes]
  [Beacon Type: 1 byte]
  [Data Length: 1 byte]
  [UUID: 16 bytes]
  [Major: 2 bytes]
  [Minor: 2 bytes]
  [TX Power: 1 byte (signed)]
  ```

**Example Beacon Data:**
```
Company ID: 0x4C00
Type: 0x02
Length: 0x15
UUID: FDA50693-A4E2-4FB1-AFCF-C6EB07647825
Major: 0x0001 (1)
Minor: 0x0042 (66)
TX Power: -59 dBm (at 1 meter)
```

**BleX Parsing Logic** (`BleScanner.kt`):
```kotlin
private fun parseIBeacon(data: ByteArray): ParsedBeacon? {
    if (data.size < 23) return null
    if (data[0] != 0x02.toByte() || data[1] != 0x15.toByte()) return null
    
    // Extract UUID (bytes 2-17)
    val uuid = buildString {
        for (i in 2..17) {
            append(String.format("%02X", data[i]))
            if (i == 5 || i == 7 || i == 9 || i == 11) append("-")
        }
    }
    
    // Extract Major (bytes 18-19, big-endian)
    val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)
    
    // Extract Minor (bytes 20-21, big-endian)
    val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)
    
    return ParsedBeacon(
        type = "iBeacon",
        ibeaconUuid = uuid,
        ibeaconMajor = major,
        ibeaconMinor = minor
    )
}
```

---

#### Eddystone (Google)
**Technical Specification:**
- **Service UUID**: 0xFEAA
- **Frame Types**:
  - **UID (0x00)**: Unique beacon identifier
  - **URL (0x10)**: Compressed URL
  - **TLM (0x20)**: Telemetry (battery, temperature)
  - **EID (0x30)**: Ephemeral identifier (rotating)

**Eddystone-UID Structure:**
```
[Frame Type: 1 byte] = 0x00
[TX Power: 1 byte (signed)]
[Namespace: 10 bytes]
[Instance: 6 bytes]
[RFU: 2 bytes]
```

**Example Beacon Data:**
```
Service UUID: 0xFEAA
Frame Type: 0x00 (UID)
TX Power: -18 dBm
Namespace: 0x00010203040506070809
Instance: 0xAABBCCDDEEFF
```

**BleX Parsing Logic** (`BleScanner.kt`):
```kotlin
private fun parseEddystone(data: ByteArray?): ParsedBeacon {
    if (data == null || data.isEmpty()) {
        return ParsedBeacon(type = "Eddystone")
    }
    
    val frameType = data[0].toInt() and 0xFF
    
    return when (frameType) {
        0x00 -> {
            // Eddystone-UID
            var namespace: String? = null
            var instance: String? = null
            
            if (data.size >= 12) {
                namespace = buildString {
                    for (i in 2..11) append(String.format("%02X", data[i]))
                }
            }
            if (data.size >= 18) {
                instance = buildString {
                    for (i in 12..17) append(String.format("%02X", data[i]))
                }
            }
            
            ParsedBeacon(
                type = "Eddystone-UID",
                eddystoneNamespace = namespace,
                eddystoneInstance = instance
            )
        }
        0x10 -> ParsedBeacon(type = "Eddystone-URL")
        0x20 -> ParsedBeacon(type = "Eddystone-TLM")
        0x30 -> ParsedBeacon(type = "Eddystone-EID")
        else -> ParsedBeacon(type = "Eddystone")
    }
}
```

---

### Recommended Beacon Hardware

| Model | Type | Battery Life | Range | TX Power | Price |
|-------|------|--------------|-------|----------|-------|
| Estimote Beacon | iBeacon | 2-3 years | 70m | Adjustable | $25-35 |
| Kontakt.io Beacon | iBeacon/Eddystone | 5+ years | 100m | -30 to +4 dBm | $20-30 |
| Nordic Thingy:52 | Eddystone | 1 year | 50m | 0 dBm | $40-50 |
| Minew MS50SF | iBeacon | 3 years | 80m | -20 to +4 dBm | $8-12 |
| Ruuvi Tag | Eddystone | 10+ years | 30m | +4 dBm | $35-40 |

**Procurement Considerations:**
- **Battery Type**: CR2032 (replaceable) vs. built-in rechargeable
- **IP Rating**: IP67+ for outdoor/industrial use
- **Mounting**: Adhesive vs. screw mount vs. keychain
- **Configuration**: Mobile app vs. NFC vs. button
- **Broadcast Rate**: 100ms to 10 seconds (balance battery vs. latency)

---

## Edge Scanner Devices

### Raspberry Pi Scanner

#### Hardware Specification

**Recommended Models:**
- **Raspberry Pi 4 Model B (4GB)** - Production recommended
- **Raspberry Pi 3 Model B+** - Budget option
- **Raspberry Pi Zero 2 W** - Ultra-compact deployments

**Minimum Requirements:**
| Component | Specification |
|-----------|---------------|
| CPU | Quad-core ARM Cortex-A72 @ 1.5 GHz (Pi 4) |
| RAM | 2GB (minimum), 4GB (recommended) |
| Storage | 16GB microSD card (Class 10, A1 rating) |
| Bluetooth | Bluetooth 5.0 BLE (built-in) |
| WiFi | 802.11ac dual-band (2.4/5 GHz) |
| Power | 5V 3A USB-C (Pi 4), 5V 2.5A micro-USB (Pi 3) |
| Ports | Ethernet (optional for wired deployment) |

**Peripheral Requirements:**
- **Power Supply**: Official Raspberry Pi power adapter (avoid cheap clones)
- **Case**: Passive cooling recommended, active cooling optional
- **SD Card**: SanDisk Extreme or Samsung EVO Plus recommended
- **Mounting**: Wall mount bracket or DIN rail mount for industrial

**Total Cost per Unit**: ~$75-120 (including peripherals)

---

#### Software Stack

**Operating System:**
- **Raspberry Pi OS Lite** (64-bit, no desktop)
- Kernel: Linux 6.1+
- Minimal installation (~2GB disk usage)

**System Dependencies:**
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y \
    python3 python3-pip python3-venv \
    bluez bluez-hcidump \
    network-manager \
    git curl wget
```

**Python Dependencies** (`requirements.txt`):
```
bleak==0.21.1          # BLE scanning library
paho-mqtt==1.6.1       # MQTT client
Flask==3.0.0           # Provisioning HTTP server
requests==2.31.0       # HTTP client
python-dotenv==1.0.0   # Environment variables
```

**BlueZ Configuration** (`/etc/bluetooth/main.conf`):
```ini
[General]
Name = BleX-Scanner
Class = 0x000100
DiscoverableTimeout = 0

[Policy]
AutoEnable=true
```

---

#### Scanner Firmware

**Main Process** (`scanner_main.py`):
```python
import asyncio
import json
from bleak import BleakScanner
import paho.mqtt.client as mqtt
import os
from datetime import datetime

class BleXScanner:
    def __init__(self):
        self.mqtt_host = os.getenv('MQTT_HOST', '192.168.1.100')
        self.mqtt_port = int(os.getenv('MQTT_PORT', 1883))
        self.scanner_id = self.get_mac_address()
        self.mqtt_client = None
        
    def get_mac_address(self):
        """Get WiFi MAC address as scanner ID"""
        with open('/sys/class/net/wlan0/address', 'r') as f:
            return f.read().strip()
    
    def connect_mqtt(self):
        """Connect to MQTT broker"""
        self.mqtt_client = mqtt.Client(client_id=f"scanner_{self.scanner_id}")
        self.mqtt_client.username_pw_set("scanner", "password123")
        self.mqtt_client.connect(self.mqtt_host, self.mqtt_port)
        self.mqtt_client.loop_start()
        print(f"Connected to MQTT broker at {self.mqtt_host}:{self.mqtt_port}")
    
    async def scan_callback(self, device, advertisement_data):
        """Process BLE advertisement"""
        payload = {
            "scanner_id": self.scanner_id,
            "beacon_mac": device.address,
            "rssi": advertisement_data.rssi,
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "tx_power": advertisement_data.tx_power,
            "name": device.name
        }
        
        topic = f"blex/scans/{self.scanner_id}"
        self.mqtt_client.publish(topic, json.dumps(payload), qos=1)
    
    async def run(self):
        """Main scanning loop"""
        self.connect_mqtt()
        
        scanner = BleakScanner()
        scanner.register_detection_callback(self.scan_callback)
        
        print("Starting BLE scan...")
        await scanner.start()
        
        # Run forever
        try:
            while True:
                await asyncio.sleep(1)
        except KeyboardInterrupt:
            await scanner.stop()
            self.mqtt_client.disconnect()

if __name__ == "__main__":
    scanner = BleXScanner()
    asyncio.run(scanner.run())
```

**Provisioning Service** (`provisioner_service.py`):
```python
from flask import Flask, request, jsonify
import json
import subprocess
import os

app = Flask(__name__)
PORT = 8888

@app.route('/provision', methods=['POST'])
def provision():
    """Receive WiFi and MQTT config from Android app"""
    data = request.get_json()
    
    ssid = data.get('ssid')
    psk = data.get('psk')
    mqtt_host = data.get('mqtt_host')
    mqtt_port = data.get('mqtt_port', 1883)
    
    if not ssid or not psk or not mqtt_host:
        return jsonify({"status": "error", "message": "Missing required fields"}), 400
    
    # Save MQTT config
    mqtt_config = {
        "mqtt_host": mqtt_host,
        "mqtt_port": mqtt_port
    }
    with open(os.path.expanduser('~/mqtt_config.json'), 'w') as f:
        json.dump(mqtt_config, f)
    
    # Configure WiFi via nmcli
    try:
        # Remove existing connection with same name
        subprocess.run(["sudo", "nmcli", "connection", "delete", ssid], 
                       capture_output=True)
        
        # Add new connection
        subprocess.run([
            "sudo", "nmcli", "connection", "add",
            "type", "wifi",
            "ifname", "wlan0",
            "con-name", ssid,
            "ssid", ssid,
            "wifi-sec.key-mgmt", "wpa-psk",
            "wifi-sec.psk", psk,
            "connection.autoconnect", "yes",
            "connection.autoconnect-priority", "10"
        ], check=True)
        
        # Disconnect from setup network
        subprocess.run(["sudo", "nmcli", "connection", "down", "setup"], 
                       capture_output=True)
        
        # Connect to site WiFi
        subprocess.Popen(["sudo", "nmcli", "connection", "up", ssid])
        
        return jsonify({"status": "ok", "message": "Provisioned successfully"})
    
    except subprocess.CalledProcessError as e:
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=PORT)
```

**UDP Discovery Beacon** (`discovery_broadcast.py`):
```python
import socket
import json
import time

def get_mac_address():
    with open('/sys/class/net/wlan0/address', 'r') as f:
        return f.read().strip()

def broadcast_discovery():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    
    mac = get_mac_address()
    message = json.dumps({"mac": mac, "type": "pi"})
    
    while True:
        try:
            sock.sendto(message.encode(), ('<broadcast>', 9000))
            print(f"Broadcasted: {message}")
            time.sleep(5)
        except Exception as e:
            print(f"Error: {e}")
            time.sleep(10)

if __name__ == "__main__":
    broadcast_discovery()
```

**Systemd Service** (`/etc/systemd/system/blex-scanner.service`):
```ini
[Unit]
Description=BleX Scanner Service
After=network.target bluetooth.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/blex-scanner
ExecStart=/home/pi/blex-scanner/venv/bin/python scanner_main.py
Restart=always
RestartSec=10
Environment="MQTT_HOST=192.168.1.100"
Environment="MQTT_PORT=1883"

[Install]
WantedBy=multi-user.target
```

---

### ESP32 Scanner

#### Hardware Specification

**Recommended Modules:**
- **ESP32-WROOM-32** - Standard module
- **ESP32-WROVER** - With external PSRAM (for heavy workloads)
- **ESP32-C3** - Budget option, RISC-V core

**Specifications:**
| Component | Specification |
|-----------|---------------|
| CPU | Xtensa dual-core 32-bit @ 240 MHz |
| RAM | 520 KB SRAM |
| Flash | 4MB (minimum) |
| Bluetooth | BLE 4.2, BLE 5.0 (mesh capable) |
| WiFi | 802.11 b/g/n (2.4 GHz) |
| Power | 3.3V, 80mA (idle), 240mA (WiFi+BLE active) |
| GPIO | 34 pins (not all usable) |

**Development Boards:**
- **ESP32 DevKitC** - Official Espressif board (~$10)
- **NodeMCU-32S** - Popular with USB-C (~$8)
- **TTGO T-Display** - With built-in LCD (~$15)

**Power Options:**
- **USB 5V** - For fixed installations
- **LiPo Battery** - 3.7V 2000mAh for portable (~8-12 hours)
- **Solar + Battery** - For remote outdoor locations

**Total Cost per Unit**: ~$10-20 (including enclosure)

---

#### Firmware Architecture

**Arduino Framework** (`scanner_firmware.ino`):
```cpp
#include <WiFi.h>
#include <PubSubClient.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include <ArduinoJson.h>

// Configuration
const char* WIFI_SSID = "SiteWiFi";
const char* WIFI_PASS = "password";
const char* MQTT_SERVER = "192.168.1.100";
const int MQTT_PORT = 1883;
const int SCAN_DURATION = 5; // seconds

// Global objects
WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);
BLEScan* bleScan;
String scannerMAC;

// BLE Scan callback
class BleXAdvertisedDeviceCallbacks : public BLEAdvertisedDeviceCallbacks {
    void onResult(BLEAdvertisedDevice advertisedDevice) {
        // Create JSON payload
        StaticJsonDocument<256> doc;
        doc["scanner_id"] = scannerMAC;
        doc["beacon_mac"] = advertisedDevice.getAddress().toString().c_str();
        doc["rssi"] = advertisedDevice.getRSSI();
        doc["timestamp"] = millis();
        
        if (advertisedDevice.haveTXPower()) {
            doc["tx_power"] = advertisedDevice.getTXPower();
        }
        
        if (advertisedDevice.haveName()) {
            doc["name"] = advertisedDevice.getName().c_str();
        }
        
        // Serialize and publish
        char payload[256];
        serializeJson(doc, payload);
        
        String topic = "blex/scans/" + scannerMAC;
        mqttClient.publish(topic.c_str(), payload, false);
    }
};

void setup() {
    Serial.begin(115200);
    delay(1000);
    
    // Get MAC address
    scannerMAC = WiFi.macAddress();
    Serial.println("BleX ESP32 Scanner");
    Serial.println("MAC: " + scannerMAC);
    
    // Initialize WiFi
    connectWiFi();
    
    // Initialize MQTT
    mqttClient.setServer(MQTT_SERVER, MQTT_PORT);
    connectMQTT();
    
    // Initialize BLE
    BLEDevice::init("BleX-Scanner");
    bleScan = BLEDevice::getScan();
    bleScan->setAdvertisedDeviceCallbacks(new BleXAdvertisedDeviceCallbacks());
    bleScan->setActiveScan(true);
    bleScan->setInterval(100);
    bleScan->setWindow(99);
    
    Serial.println("Setup complete. Starting scan loop...");
}

void loop() {
    // Maintain connections
    if (WiFi.status() != WL_CONNECTED) {
        connectWiFi();
    }
    if (!mqttClient.connected()) {
        connectMQTT();
    }
    mqttClient.loop();
    
    // Perform BLE scan
    Serial.println("Scanning...");
    BLEScanResults foundDevices = bleScan->start(SCAN_DURATION, false);
    bleScan->clearResults();
    
    delay(1000);
}

void connectWiFi() {
    Serial.print("Connecting to WiFi...");
    WiFi.begin(WIFI_SSID, WIFI_PASS);
    
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20) {
        delay(500);
        Serial.print(".");
        attempts++;
    }
    
    if (WiFi.status() == WL_CONNECTED) {
        Serial.println(" Connected!");
        Serial.print("IP: ");
        Serial.println(WiFi.localIP());
    } else {
        Serial.println(" Failed!");
        delay(5000);
    }
}

void connectMQTT() {
    Serial.print("Connecting to MQTT...");
    
    String clientId = "esp32_" + scannerMAC;
    int attempts = 0;
    
    while (!mqttClient.connected() && attempts < 5) {
        if (mqttClient.connect(clientId.c_str(), "scanner", "password123")) {
            Serial.println(" Connected!");
            return;
        } else {
            Serial.print(".");
            delay(2000);
            attempts++;
        }
    }
    
    if (!mqttClient.connected()) {
        Serial.println(" Failed!");
    }
}
```

**Compilation Settings** (`platformio.ini`):
```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino

lib_deps = 
    knolleary/PubSubClient@^2.8
    bblanchon/ArduinoJson@^6.21.3
    h2zero/NimBLE-Arduino@^1.4.1

monitor_speed = 115200
upload_speed = 921600

build_flags = 
    -DCORE_DEBUG_LEVEL=3
    -DARDUINO_USB_CDC_ON_BOOT=1
```

---

## Android Tablet Hub

### Hardware Requirements

**Minimum Specifications:**
| Component | Requirement |
|-----------|-------------|
| OS | Android 12+ (API 31+) |
| CPU | Octa-core @ 2.0 GHz |
| RAM | 4GB |
| Storage | 64GB |
| Display | 10" (1920x1200) |
| Battery | 7000mAh+ |
| Bluetooth | BLE 5.0+ |
| WiFi | 802.11ac (WiFi 5) |
| Ports | USB-C (OTG support) |

**Recommended Models:**
- **Samsung Galaxy Tab A8** (~$230) - Best value
- **Samsung Galaxy Tab S8** (~$700) - Premium option
- **Lenovo Tab P11 Pro** (~$400) - Good balance
- **OnePlus Pad** (~$480) - High performance

**Accessories:**
- **Wall Mount**: Tablet holder with charging cable management
- **Protective Case**: Rugged case for industrial environments
- **Power Supply**: 25W+ USB-C PD charger for continuous operation

---

### Software Architecture

#### Application Structure
```
com.blex.app/
├── MainActivity.kt                    # App entry point
├── BleXApp.kt                         # Application class
├── BleScannerService.kt               # Foreground service
├── BleScanner.kt                      # BLE scanning logic
├── MqttManager.kt                     # MQTT client
├── EmbeddedBroker.kt                  # Moquette broker
├── MqttBridge.kt                      # Local→Remote forwarding
├── PayloadBuilder.kt                  # JSON serialization
├── ServiceHealth.kt                   # Health monitoring
├── data/
│   ├── SettingsManager.kt             # SharedPreferences
│   ├── ScanRepository.kt              # Data layer
│   └── BeaconData.kt                  # Data models
├── network/
│   ├── ApiClient.kt                   # Backend HTTP client
│   └── ProvisioningClient.kt          # Scanner provisioning
└── ui/
    ├── screens/
    │   ├── ScannerScreen.kt           # Main dashboard
    │   ├── SettingsScreen.kt          # Configuration
    │   ├── LogScreen.kt               # System logs
    │   └── configurator/
    │       ├── HotspotTab.kt          # WiFi hotspot config
    │       ├── ScannersTab.kt         # Scanner management
    │       ├── ZonesTab.kt            # Zone definitions
    │       └── AssetsTab.kt           # Asset registry
    └── theme/
        └── Theme.kt                   # Material 3 theming
```

#### Service Lifecycle

**Foreground Service** (`BleScannerService.kt`):
```kotlin
class BleScannerService : Service() {
    private lateinit var bleScanner: BleScanner
    private lateinit var mqttManager: MqttManager
    private lateinit var broker: EmbeddedBroker
    private lateinit var bridge: MqttBridge
    private lateinit var healthMonitor: ServiceHealth
    
    override fun onCreate() {
        super.onCreate()
        
        // Create notification channel
        createNotificationChannel()
        
        // Start foreground with notification
        startForeground(NOTIFICATION_ID, buildNotification())
        
        // Initialize components
        broker = EmbeddedBroker(this)
        mqttManager = MqttManager(this)
        bridge = MqttBridge(this)
        bleScanner = BleScanner(this)
        healthMonitor = ServiceHealth(this)
        
        // Start broker
        if (settings.brokerEnabled) {
            broker.start()
        }
        
        // Connect MQTT
        mqttManager.connect()
        
        // Start bridge
        if (settings.bridgeEnabled) {
            bridge.start()
        }
        
        // Start scanning
        bleScanner.startScanning()
        bleScanner.onScanBatchReady = { beacons ->
            publishBeacons(beacons)
        }
        
        // Start health monitor
        healthMonitor.startWatchdog()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Auto-restart on crash
    }
    
    override fun onDestroy() {
        bleScanner.stopScanning()
        mqttManager.disconnect()
        bridge.stop()
        broker.stop()
        healthMonitor.stopWatchdog()
        super.onDestroy()
    }
}
```

**Watchdog Monitor** (`ServiceHealth.kt`):
```kotlin
class ServiceHealth(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var watchdogJob: Job? = null
    
    fun startWatchdog() {
        watchdogJob = scope.launch {
            while (isActive) {
                delay(60_000) // Check every 60 seconds
                
                // Check BLE scanner
                if (!bleScanner.isScanning) {
                    log("Scanner stopped, restarting...")
                    bleScanner.forceRestart()
                }
                
                // Check MQTT connection
                if (!mqttManager.isConnected) {
                    log("MQTT disconnected, reconnecting...")
                    mqttManager.reconnectIfNeeded()
                }
                
                // Check broker
                if (settings.brokerEnabled && !broker.isRunning) {
                    log("Broker stopped, restarting...")
                    broker.start()
                }
                
                // Check last scan time
                val timeSinceLastScan = System.currentTimeMillis() - bleScanner.lastScanResultTime
                if (timeSinceLastScan > 120_000) {
                    log("No scan results for 2 minutes, restarting scanner...")
                    bleScanner.forceRestart()
                }
            }
        }
    }
}
```

---

## Device Lifecycle Management

### Scanner Provisioning Flow

```
┌─────────────────────────────────────────────────────────────┐
│ Phase 1: Discovery                                          │
└─────────────────────────────────────────────────────────────┘

1. Scanner boots → Connects to "setup" WiFi (preconfigured)
2. Scanner runs provisioner_service.py (HTTP on port 8888)
3. Scanner broadcasts UDP: {"mac": "aa:bb:cc:dd:ee:ff"} on port 9000
4. Android tablet listens on UDP:9000
5. Tablet receives broadcast → Shows in "Discoverable Scanners" list

┌─────────────────────────────────────────────────────────────┐
│ Phase 2: Configuration                                      │
└─────────────────────────────────────────────────────────────┘

6. User selects scanner in tablet UI
7. User enters site WiFi SSID and password
8. Tablet POSTs to http://<scanner_ip>:8888/provision:
   {
     "ssid": "SiteWiFi",
     "psk": "password123",
     "mqtt_host": "192.168.1.100",
     "mqtt_port": 1883
   }
9. Scanner saves config to file
10. Scanner uses nmcli to configure WiFi
11. Scanner disconnects from "setup" WiFi

┌─────────────────────────────────────────────────────────────┐
│ Phase 3: Activation                                         │
└─────────────────────────────────────────────────────────────┘

12. Scanner connects to site WiFi
13. Scanner connects to MQTT broker at tablet IP
14. Scanner starts BLE scanning
15. Scanner publishes scan data to MQTT
16. Scanner sends heartbeat every 60 seconds
17. Tablet UI shows scanner as "Active"
```

### Firmware Update Process

**Raspberry Pi (Planned)**:
```bash
# Via SSH (manual)
ssh pi@scanner-01.local
cd ~/blex-scanner
git pull
sudo systemctl restart blex-scanner

# Via Ansible (automated)
ansible-playbook -i inventory.ini update-scanners.yml
```

**ESP32 (OTA - Planned)**:
```cpp
#include <ArduinoOTA.h>

void setupOTA() {
    ArduinoOTA.setHostname("blex-scanner");
    ArduinoOTA.setPassword("update123");
    
    ArduinoOTA.onStart([]() {
        Serial.println("OTA Update Starting...");
    });
    
    ArduinoOTA.onEnd([]() {
        Serial.println("OTA Update Complete!");
    });
    
    ArduinoOTA.onError([](ota_error_t error) {
        Serial.printf("OTA Error[%u]: ", error);
    });
    
    ArduinoOTA.begin();
}

void loop() {
    ArduinoOTA.handle();
    // ... rest of loop
}
```

---

## Performance Specifications

### BLE Scanning Performance

| Device | Beacons/sec | Scan Range | Power Consumption | CPU Usage |
|--------|-------------|------------|-------------------|-----------|
| Raspberry Pi 4 | 200-300 | 50m (indoor) | 2-3W | 15-25% |
| ESP32 | 50-100 | 30m (indoor) | 0.5-1W | 60-80% |
| Android Tablet | 100-500 | 40m (indoor) | 3-5W | 10-20% |

### MQTT Performance

| Metric | Value | Notes |
|--------|-------|-------|
| Publish Rate (Pi) | 10-50 msgs/sec | Depends on beacon density |
| Publish Rate (ESP32) | 5-20 msgs/sec | Limited by CPU |
| Broker Throughput | 1000+ msgs/sec | Moquette on tablet |
| Message Size | 150-250 bytes | JSON payload |
| QoS | 1 (default) | At-least-once delivery |
| Latency (local) | <10ms | Broker on same device |
| Latency (remote) | 100-500ms | Via WSS to cloud |

### Battery Life Estimates

**Raspberry Pi**: Requires constant power (5V 2.5-3A)

**ESP32**:
- Continuous operation: 8-12 hours (2000mAh LiPo)
- With deep sleep (1 min scan, 5 min sleep): 7-10 days

**Android Tablet**:
- Screen ON + scanning: 8-12 hours
- Screen OFF + scanning: 24-48 hours
- Recommendation: Keep plugged in for 24/7 operation

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Dec 2024 | System Team | Initial release |

**Next Review Date**: May 2026

---

*This document is part of the BleX technical documentation suite.*