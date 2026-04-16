# BleX IoT Technical Architecture - Communication Protocols

**Document Version:** 1.0  
**Last Updated:** 16th of April 2026  
**Status:** Production  
**Classification:** Internal

---

## Table of Contents
1. [Protocol Overview](#protocol-overview)
2. [Bluetooth Low Energy (BLE)](#bluetooth-low-energy-ble)
3. [MQTT Protocol](#mqtt-protocol)
4. [HTTP/HTTPS](#httphttps)
5. [UDP Broadcast](#udp-broadcast)
6. [WebSocket (WSS)](#websocket-wss)
7. [Protocol Security](#protocol-security)
8. [Message Flow Diagrams](#message-flow-diagrams)

---

## Protocol Overview

The BleX system utilizes multiple communication protocols, each optimized for specific purposes:

| Protocol | Purpose | Direction | Encryption |
|----------|---------|-----------|------------|
| **BLE** | Asset broadcasting | Beacon → Scanner | None (open broadcast) |
| **MQTT** | Sensor data transport | Scanner → Broker | Optional TLS |
| **HTTP** | Scanner provisioning | Tablet → Scanner | None (local only) |
| **UDP** | Scanner discovery | Scanner → Tablet | None (local only) |
| **WSS** | Cloud bridge | Tablet → Backend | TLS 1.3 mandatory |
| **HTTPS** | API communication | Client → Backend | TLS 1.2+ |

### Protocol Stack Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │   BLE    │  │   MQTT   │  │   HTTP   │  │    UDP   │   │
│  │ Advert.  │  │  Publish │  │   POST   │  │ Broadcast│   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                    Transport Layer                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │    L2CAP │  │    TCP   │  │    TCP   │  │    UDP   │   │
│  │  (BLE)   │  │  (MQTT)  │  │  (HTTP)  │  │          │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                     Network Layer                           │
│                      IPv4 / IPv6                            │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                  Data Link Layer                            │
│           WiFi (802.11) / Bluetooth 4.0+                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Bluetooth Low Energy (BLE)

### Protocol Specification

**Standard**: Bluetooth Core Specification v4.0 - v5.3  
**Frequency**: 2.4 GHz ISM band (2.402 - 2.480 GHz)  
**Channels**: 40 channels (3 advertising, 37 data)  
**Modulation**: GFSK (Gaussian Frequency Shift Keying)  
**Data Rate**: 1 Mbps (BLE 4.x), up to 2 Mbps (BLE 5.0)  
**Range**: 10-100 meters (depends on TX power and environment)

### Advertisement Packet Structure

**Generic BLE Advertisement**:
```
┌─────────────────────────────────────────────────────────┐
│  Preamble (1 byte) | Access Address (4 bytes)          │
├─────────────────────────────────────────────────────────┤
│  PDU Header (2 bytes)                                   │
│    - PDU Type: ADV_IND (0x00) = Connectable undirected │
│    - TxAdd: Public (0) or Random (1)                    │
│    - RxAdd: Not used in advertising                     │
│    - Length: Payload size (6-37 bytes)                  │
├─────────────────────────────────────────────────────────┤
│  Advertising Address (6 bytes)                          │
│    - MAC address of the beacon                          │
├─────────────────────────────────────────────────────────┤
│  Advertising Data (0-31 bytes)                          │
│    - AD Structures (Type-Length-Value)                  │
│      * Flags (0x01)                                     │
│      * Complete Local Name (0x09)                       │
│      * Manufacturer Specific Data (0xFF)                │
│      * Service UUIDs (0x03, 0x16)                       │
├─────────────────────────────────────────────────────────┤
│  CRC (3 bytes)                                          │
└─────────────────────────────────────────────────────────┘
```

### iBeacon Advertisement Format

**Apple iBeacon** (Manufacturer Specific Data: 0x004C):
```
┌───────────────────────────────────────────────────────────┐
│ Company ID: 0x004C (Apple Inc.)            [2 bytes]      │
├───────────────────────────────────────────────────────────┤
│ Beacon Type: 0x02 (iBeacon)                [1 byte]       │
├───────────────────────────────────────────────────────────┤
│ Data Length: 0x15 (21 bytes)               [1 byte]       │
├───────────────────────────────────────────────────────────┤
│ Proximity UUID                             [16 bytes]     │
│   Example: FDA50693-A4E2-4FB1-AFCF-C6EB07647825          │
├───────────────────────────────────────────────────────────┤
│ Major Number (big-endian)                  [2 bytes]      │
│   Range: 0x0000 - 0xFFFF (0 - 65535)                      │
├───────────────────────────────────────────────────────────┤
│ Minor Number (big-endian)                  [2 bytes]      │
│   Range: 0x0000 - 0xFFFF (0 - 65535)                      │
├───────────────────────────────────────────────────────────┤
│ Measured Power (TX Power at 1 meter)       [1 byte]       │
│   Signed integer (-127 to 128 dBm)                        │
│   Typical: -59 dBm                                        │
└───────────────────────────────────────────────────────────┘
```

**Parsing Example** (Kotlin - `BleScanner.kt`):
```kotlin
fun parseIBeacon(manufacturerData: ByteArray): iBeaconData? {
    if (manufacturerData.size < 23) return null
    
    // Verify iBeacon prefix
    if (manufacturerData[0] != 0x02.toByte() || 
        manufacturerData[1] != 0x15.toByte()) {
        return null
    }
    
    // Extract UUID (bytes 2-17)
    val uuidBytes = manufacturerData.sliceArray(2..17)
    val uuid = formatUUID(uuidBytes)
    
    // Extract Major (bytes 18-19, big-endian)
    val major = ((manufacturerData[18].toInt() and 0xFF) shl 8) or
                 (manufacturerData[19].toInt() and 0xFF)
    
    // Extract Minor (bytes 20-21, big-endian)
    val minor = ((manufacturerData[20].toInt() and 0xFF) shl 8) or
                 (manufacturerData[21].toInt() and 0xFF)
    
    // Extract TX Power (byte 22, signed)
    val txPower = manufacturerData[22].toInt()
    
    return iBeaconData(uuid, major, minor, txPower)
}

fun formatUUID(bytes: ByteArray): String {
    return buildString {
        bytes.forEachIndexed { i, byte ->
            append(String.format("%02X", byte))
            if (i == 3 || i == 5 || i == 7 || i == 9) append("-")
        }
    }
}
```

### Eddystone-UID Advertisement Format

**Google Eddystone** (Service UUID: 0xFEAA):
```
┌───────────────────────────────────────────────────────────┐
│ Service UUID: 0xFEAA (Eddystone)           [2 bytes]      │
├───────────────────────────────────────────────────────────┤
│ Frame Type: 0x00 (UID)                     [1 byte]       │
├───────────────────────────────────────────────────────────┤
│ Ranging Data (Calibrated TX Power @ 0m)    [1 byte]       │
│   Signed integer, typical: -18 dBm                        │
├───────────────────────────────────────────────────────────┤
│ Namespace ID                               [10 bytes]     │
│   Unique identifier for beacon group                      │
├───────────────────────────────────────────────────────────┤
│ Instance ID                                [6 bytes]      │
│   Unique identifier for specific beacon                   │
├───────────────────────────────────────────────────────────┤
│ RFU (Reserved for Future Use)              [2 bytes]      │
│   Must be 0x00                                            │
└───────────────────────────────────────────────────────────┘
```

**Parsing Example** (Kotlin):
```kotlin
fun parseEddystoneUID(serviceData: ByteArray): EddystoneData? {
    if (serviceData.size < 18) return null
    if (serviceData[0] != 0x00.toByte()) return null // Not UID frame
    
    val txPower = serviceData[1].toInt()
    
    val namespace = buildString {
        for (i in 2..11) {
            append(String.format("%02X", serviceData[i]))
        }
    }
    
    val instance = buildString {
        for (i in 12..17) {
            append(String.format("%02X", serviceData[i]))
        }
    }
    
    return EddystoneData(namespace, instance, txPower)
}
```

### BLE Scan Modes

BleX supports three scan modes with different power/latency tradeoffs:

| Mode | Scan Interval | Scan Window | Latency | Power | Use Case |
|------|---------------|-------------|---------|-------|----------|
| **LOW_POWER** | 5000ms | 500ms | ~5s | Low | Battery operation |
| **BALANCED** | 1000ms | 500ms | ~1s | Medium | Default mode |
| **LOW_LATENCY** | 100ms | 100ms | ~100ms | High | Critical tracking |

**Configuration** (Android - `BleScanner.kt`):
```kotlin
val scanSettings = ScanSettings.Builder()
    .setScanMode(when (powerMode) {
        "LOW_POWER" -> ScanSettings.SCAN_MODE_LOW_POWER
        "LOW_LATENCY" -> ScanSettings.SCAN_MODE_LOW_LATENCY
        else -> ScanSettings.SCAN_MODE_BALANCED
    })
    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
    .setReportDelay(0) // Real-time callback
    .build()
```

### RSSI (Received Signal Strength Indicator)

**Definition**: Measure of signal power received by the scanner antenna

**Range**: -100 dBm (very weak) to -30 dBm (very strong)  
**Typical Values**:
- **-30 to -50 dBm**: Excellent signal (< 1 meter)
- **-50 to -70 dBm**: Good signal (1-5 meters)
- **-70 to -85 dBm**: Fair signal (5-15 meters)
- **-85 to -100 dBm**: Weak signal (15-30 meters)

**Distance Estimation** (Approximate):
```kotlin
fun estimateDistance(rssi: Int, txPower: Int): Double {
    val ratio = rssi.toDouble() / txPower.toDouble()
    return if (ratio < 1.0) {
        Math.pow(ratio, 10.0)
    } else {
        (0.89976) * Math.pow(ratio, 7.7095) + 0.111
    }
}
```

**Note**: RSSI-based distance is unreliable due to:
- Multipath interference
- Antenna orientation
- Human body absorption
- Environmental factors (walls, metal)

**BleX Approach**: Use RSSI for **relative comparison** (strongest signal = closest scanner), not absolute distance.

---

## MQTT Protocol

### Protocol Specification

**Version**: MQTT v3.1.1 (primary), v5.0 (optional)  
**Transport**: TCP (port 1883), TLS (port 8883), WebSocket (port 80/443)  
**Architecture**: Publish/Subscribe (Pub/Sub) messaging  
**Broker**: Moquette 0.17 (embedded on Android tablet)

### MQTT Architecture

```
┌──────────────────────────────────────────────────────────┐
│                   MQTT Broker (Tablet)                   │
│                  Moquette on port 1883                   │
└───────────────────────┬──────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┬──────────────┐
        │               │               │              │
        ▼               ▼               ▼              ▼
   ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
   │Scanner 1│    │Scanner 2│    │Scanner 3│    │ Tablet  │
   │  (Pi)   │    │ (ESP32) │    │  (Pi)   │    │ Scanner │
   └─────────┘    └─────────┘    └─────────┘    └─────────┘
        │               │               │              │
        │ Publish       │ Publish       │ Publish      │ Publish
        ▼               ▼               ▼              ▼
   Topic: blex/scanner/aa:bb:cc:dd:ee:f1
   Topic: blex/scanner/aa:bb:cc:dd:ee:f2
   Topic: blex/scanner/aa:bb:cc:dd:ee:f3
   Topic: blex/scanner/tablet_123
                        │
                        │ Subscribe (#)
                        ▼
                  ┌──────────┐
                  │  Bridge  │
                  │Component │
                  └─────┬────┘
                        │
                        │ Forward via WSS
                        ▼
              ┌────────────────────┐
              │  Remote MQTT       │
              │  Broker (Cloud)    │
              └────────────────────┘
```

### Topic Structure

**BleX Topic Hierarchy**:
```
blex/
├── scanner/
│   ├── {scanner_id}/           # Scanner publishes here
│   │   └── payload: BeaconData JSON
│   └── tablet_{device_id}/     # Tablet publishes here
│       └── payload: BeaconData JSON
├── heartbeat/
│   └── {scanner_id}/           # Scanner health status
│       └── payload: { "status": "ok", "uptime": 12345 }
├── commands/
│   └── {scanner_id}/           # Control commands (future)
│       └── payload: { "action": "restart" }
└── events/
    └── movement/               # Zone transition events
        └── payload: MovementEvent JSON
```

**Topic Naming Rules**:
- Lowercase only
- Forward slash `/` as hierarchy separator
- Avoid special characters (except `-`, `_`)
- Use device ID (MAC address) for uniqueness

### Message Payload Format

**Beacon Scan Data** (Published by scanners):
```json
{
  "scanner_id": "b8:27:eb:12:34:56",
  "beacon_mac": "AC:23:3F:A1:B2:C3",
  "rssi": -67,
  "timestamp": "2024-12-19T10:30:45.123Z",
  "tx_power": -59,
  "name": "Asset_001",
  "beacon_type": "iBeacon",
  "ibeacon_uuid": "FDA50693-A4E2-4FB1-AFCF-C6EB07647825",
  "ibeacon_major": 1,
  "ibeacon_minor": 42
}
```

**Heartbeat** (Published by scanners every 60s):
```json
{
  "scanner_id": "b8:27:eb:12:34:56",
  "status": "ok",
  "uptime": 86400,
  "timestamp": "2024-12-19T10:30:45.123Z",
  "wifi_rssi": -45,
  "beacons_detected": 23,
  "mqtt_published": 1543
}
```

**Movement Event** (Published by decision engine):
```json
{
  "asset_mac": "AC:23:3F:A1:B2:C3",
  "from_zone_id": 1,
  "to_zone_id": 2,
  "deciding_rssi": -62.5,
  "timestamp": "2024-12-19T10:30:45.123Z",
  "state": "ZONE"
}
```

### Quality of Service (QoS)

MQTT supports three QoS levels:

| QoS | Name | Guarantee | Use Case | BleX Usage |
|-----|------|-----------|----------|------------|
| **0** | At most once | Fire and forget | Sensor data where loss is acceptable | Not used |
| **1** | At least once | Acknowledged delivery | Default for most messages | **Default** |
| **2** | Exactly once | Four-way handshake | Critical commands | Reserved |

**BleX Default**: QoS 1 for balance between reliability and performance

**Configuration** (Android - `MqttManager.kt`):
```kotlin
val message = MqttMessage(payload.toByteArray()).apply {
    qos = settings.mqttQos // Default: 1
    isRetained = false
}
mqttClient.publish(topic, message)
```

### Retained Messages

**Definition**: Messages stored by broker and delivered to new subscribers immediately

**BleX Usage**: Not used (beacon data is ephemeral)

**Alternative**: Last Will and Testament (LWT) for disconnect detection

```kotlin
val connectOptions = MqttConnectOptions().apply {
    setWill(
        "blex/heartbeat/${deviceId}",
        "{\"status\":\"offline\"}".toByteArray(),
        1, // QoS
        true // Retained
    )
}
```

### Authentication

**Methods Supported**:
1. **Anonymous**: No credentials (default for local broker)
2. **Username/Password**: Simple auth (recommended)
3. **TLS Client Certificates**: Mutual TLS (enterprise)

**Configuration** (Moquette - `EmbeddedBroker.kt`):
```kotlin
val props = Properties().apply {
    setProperty("host", "0.0.0.0")
    setProperty("port", "1883")
    setProperty("allow_anonymous", "false")
    setProperty("password_file", "$dataDir/password_file.conf")
}

// Password file format:
// username:password
File("$dataDir/password_file.conf").writeText(
    "scanner:${settings.brokerPassword}\n" +
    "bridge:${settings.brokerPassword}\n"
)
```

### TLS Encryption

**Local MQTT**: Optional (trusted network)  
**Remote MQTT**: Mandatory (internet)

**Certificate Types**:
1. **System CA**: Uses Android/OS trust store
2. **Custom CA**: User uploads certificate file
3. **Self-Signed**: Requires hostname verification bypass

**TLS Configuration** (Android - `MqttManager.kt`):
```kotlin
val sslContext = SSLContext.getInstance("TLS")

if (settings.remoteCaCertUri.isNotEmpty()) {
    // Load custom CA certificate
    val cf = CertificateFactory.getInstance("X.509")
    val caInput = contentResolver.openInputStream(Uri.parse(settings.remoteCaCertUri))
    val ca = caInput.use { cf.generateCertificate(it) as X509Certificate }
    
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        setCertificateEntry("ca", ca)
    }
    
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(keyStore)
    
    sslContext.init(null, tmf.trustManagers, SecureRandom())
} else {
    // Use system CA or accept all (if not strict)
    sslContext.init(null, null, SecureRandom())
}

val options = MqttConnectOptions().apply {
    socketFactory = sslContext.socketFactory
}
```

**Hostname Verification Bypass** (for raw IPs):
```kotlin
class HostnameInsensitiveSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
    private fun patch(socket: Socket): Socket {
        if (socket is SSLSocket) {
            val params = socket.sslParameters
            params.endpointIdentificationAlgorithm = "" // Disable hostname check
            socket.sslParameters = params
        }
        return socket
    }
    
    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        return patch(delegate.createSocket(s, host, port, autoClose))
    }
    // ... other overrides
}
```

---

## HTTP/HTTPS

### Scanner Provisioning (HTTP)

**Protocol**: HTTP/1.1  
**Port**: 8888  
**Method**: POST  
**Security**: None (local network only)

**Endpoint**: `POST http://<scanner_ip>:8888/provision`

**Request Headers**:
```
Content-Type: application/json
Content-Length: 123
```

**Request Body**:
```json
{
  "ssid": "SiteWiFi_2.4GHz",
  "psk": "password123",
  "mqtt_host": "192.168.1.100",
  "mqtt_port": 1883
}
```

**Response** (Success - 200 OK):
```json
{
  "status": "ok",
  "message": "Provisioned successfully. Rebooting..."
}
```

**Response** (Error - 400 Bad Request):
```json
{
  "status": "error",
  "message": "Missing required field: ssid"
}
```

**Implementation** (Python - `provisioner_service.py`):
```python
from flask import Flask, request, jsonify
import subprocess

app = Flask(__name__)

@app.route('/provision', methods=['POST'])
def provision():
    data = request.get_json()
    
    ssid = data.get('ssid')
    psk = data.get('psk')
    mqtt_host = data.get('mqtt_host')
    
    if not all([ssid, psk, mqtt_host]):
        return jsonify({"status": "error", "message": "Missing fields"}), 400
    
    # Configure WiFi
    subprocess.run([
        "sudo", "nmcli", "connection", "add",
        "type", "wifi", "ifname", "wlan0",
        "con-name", ssid, "ssid", ssid,
        "wifi-sec.key-mgmt", "wpa-psk",
        "wifi-sec.psk", psk
    ])
    
    # Save MQTT config
    with open('/home/pi/mqtt_config.json', 'w') as f:
        json.dump({"mqtt_host": mqtt_host, "mqtt_port": data.get('mqtt_port', 1883)}, f)
    
    # Reboot to apply
    subprocess.Popen(["sudo", "reboot"])
    
    return jsonify({"status": "ok"})
```

### Backend API (HTTPS)

**Protocol**: HTTPS/1.1 (TLS 1.2+)  
**Port**: 443 (or custom)  
**Authentication**: Bearer tokens (JWT planned)  
**Format**: JSON

**Example Endpoints**:

**POST /api/asset/movement** (Submit movement event)
```bash
curl -X POST https://api.blex.example.com/api/asset/movement \
  -H "Content-Type: application/json" \
  -d '{
    "asset_mac": "AC:23:3F:A1:B2:C3",
    "from_zone_id": 1,
    "to_zone_id": 2,
    "deciding_rssi": -62.5,
    "timestamp": "2024-12-19T10:30:45Z"
  }'
```

**GET /api/assets/current** (Query current locations)
```bash
curl https://api.blex.example.com/api/assets/current
```

**Response**:
```json
[
  {
    "mac": "AC:23:3F:A1:B2:C3",
    "zone": 2,
    "last_seen": "2024-12-19T10:30:45Z",
    "rssi": -62
  }
]
```

---

## UDP Broadcast

### Scanner Discovery Protocol

**Protocol**: UDP  
**Port**: 9000  
**Direction**: Scanner → Tablet (broadcast)  
**Interval**: Every 5 seconds  
**Security**: None (local network only)

**Packet Format**:
```json
{
  "mac": "b8:27:eb:12:34:56",
  "type": "pi",
  "name": "Scanner-01"
}
```

**Broadcast Address**: `255.255.255.255` or subnet broadcast (e.g., `192.168.1.255`)

**Implementation** (Python - Scanner):
```python
import socket
import json
import time

def broadcast_discovery():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    
    message = json.dumps({
        "mac": get_mac_address(),
        "type": "pi"
    })
    
    while True:
        sock.sendto(message.encode(), ('<broadcast>', 9000))
        time.sleep(5)
```

**Implementation** (Kotlin - Tablet Listener):
```kotlin
class ProvisioningListener(private val context: Context) {
    private var socket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    fun startListening() {
        scope.launch {
            socket = DatagramSocket(9000).apply {
                broadcast = true
            }
            
            val buffer = ByteArray(1024)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)
                
                val json = String(packet.data, 0, packet.length)
                val scanner = Json.decodeFromString<ScannerDiscovery>(json)
                
                // Notify UI
                _discoveredScanners.emit(scanner)
            }
        }
    }
}
```

---

## WebSocket (WSS)

### MQTT Bridge to Cloud

**Protocol**: WebSocket Secure (WSS)  
**Port**: 443  
**Encryption**: TLS 1.3  
**Subprotocol**: `mqtt` (MQTT over WebSocket)

**URL Format**:
```
wss://mqtt.example.com:443/mqtt
```

**Configuration** (Android - `MqttBridge.kt`):
```kotlin
val remoteClient = MqttAsyncClient(
    "wss://mqtt.example.com:443/mqtt",
    "bridge_${deviceId}",
    MemoryPersistence()
)

val options = MqttConnectOptions().apply {
    isAutomaticReconnect = true
    isCleanSession = false
    socketFactory = getSSLSocketFactory()
}

remoteClient.connect(options)
```

**Frame Format** (WebSocket):
```
┌─────────────────────────────────────────────────┐
│  FIN=1 | RSV=0 | Opcode=2 (Binary)            │
├─────────────────────────────────────────────────┤
│  Mask=1 | Payload Length                        │
├─────────────────────────────────────────────────┤
│  Masking Key (4 bytes)                          │
├─────────────────────────────────────────────────┤
│  Masked Payload (MQTT packet)                   │
└─────────────────────────────────────────────────┘
```

---

## Protocol Security

### Threat Model

| Threat | Protocol | Mitigation |
|--------|----------|------------|
| Beacon spoofing | BLE | Accept only registered MAC addresses (whitelist) |
| MQTT hijacking | MQTT | Username/password auth + TLS encryption |
| Provisioning MITM | HTTP | Isolated "setup" WiFi, short-lived exposure |
| Data interception | MQTT | TLS 1.3 for cloud connections |
| Scanner impersonation | MQTT | MAC-based client ID, heartbeat validation |

### Encryption Summary

| Layer | Protocol | Encryption | Key Strength |
|-------|----------|------------|---------------|
| BLE Broadcast | None | Unencrypted | N/A |
| Local MQTT | Optional TLS 1.2 | AES-128/256 | 128-256 bit |
| Cloud MQTT | Mandatory TLS 1.3 | AES-256-GCM | 256 bit |
| Backend API | HTTPS TLS 1.2+ | AES-256 | 256 bit |

### Certificate Management

**Supported Certificate Types**:
1. **System CA** - Uses OS trust store (recommended for public domains)
2. **Custom CA** - User uploads `.pem` or `.crt` file
3. **Self-Signed** - Requires hostname verification bypass (development only)

**Certificate Upload Flow**:
1. User navigates to Settings → MQTT → TLS Settings
2. User taps "Upload CA Certificate"
3. File picker opens (filters: `.pem`, `.crt`, `.cer`)
4. App validates certificate format
5. Certificate URI saved to SharedPreferences
6. On MQTT connect, certificate is loaded from URI

---

## Message Flow Diagrams

### Beacon Scan Flow

```
Beacon                Scanner              MQTT Broker           Backend API
  │                     │                      │                      │
  │ BLE Advertisement   │                      │                      │
  ├────────────────────>│                      │                      │
  │ (MAC, RSSI, TX)     │                      │                      │
  │                     │                      │                      │
  │                     │ PUBLISH              │                      │
  │                     │ Topic: blex/scans/... │                     │
  │                     ├─────────────────────>│                      │
  │                     │ QoS 1, Payload: JSON │                      │
  │                     │                      │                      │
  │                     │ PUBACK               │                      │
  │                     │<─────────────────────┤                      │
  │                     │                      │                      │
  │                     │                      │ Bridge Forwards      │
  │                     │                      │ (if configured)      │
  │                     │                      ├─────────────────────>│
  │                     │                      │ WSS/TLS 1.3          │
  │                     │                      │                      │
  │                     │                      │                      │ Persist to DB
  │                     │                      │                      │ (PostgreSQL)
```

### Scanner Provisioning Flow

```
Tablet              Scanner (Setup Mode)        Scanner (Active Mode)
  │                        │                            │
  │ UDP Listen :9000       │                            │
  │<───────────────────────┤ UDP Broadcast              │
  │ {"mac": "..."}         │ Every 5 seconds            │
  │                        │                            │
  │ Show in UI             │                            │
  │                        │                            │
  │ User Configures        │                            │
  │ (SSID, Password, IP)   │                            │
  │                        │                            │
  │ HTTP POST :8888        │                            │
  ├───────────────────────>│ /provision                 │
  │ {ssid, psk, mqtt_host} │                            │
  │                        │                            │
  │<───────────────────────┤ 200 OK                     │
  │ {"status": "ok"}       │                            │
  │                        │                            │
  │                        │ Save Config                │
  │                        │ Reboot                     │
  │                        │                            │
  │                        │                            │
  │                        │         Connect to Site WiFi
  │                        │         Connect to MQTT
  │                        │         Start Scanning
  │                        │                            │
  │ MQTT Subscribe         │                            │
  │ blex/heartbeat/+       │                            │
  │<─────────────────────────────────────────────────────┤
  │                        │                    Heartbeat (60s)
  │                        │                            │
```

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Apr 2026 | Akshat Jain | Initial release |

**Next Review Date**: May 2026

---

*This document is part of the BleX technical documentation suite.*