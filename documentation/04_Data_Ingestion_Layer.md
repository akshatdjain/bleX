# BleX IoT Technical Architecture - Data Ingestion Layer

**Document Version:** 1.0  
**Last Updated:** 16th of April 2026 
**Status:** Production  
**Classification:** Internal

---

## Table of Contents
1. [Overview](#overview)
2. [Ingestion Architecture](#ingestion-architecture)
3. [Data Sources](#data-sources)
4. [Data Pipeline](#data-pipeline)
5. [Data Transformation](#data-transformation)
6. [Data Validation](#data-validation)
7. [Buffering and Queueing](#buffering-and-queueing)
8. [Performance Optimization](#performance-optimization)
9. [Monitoring and Observability](#monitoring-and-observability)

---

## Overview

The BleX Data Ingestion Layer is responsible for collecting, validating, transforming, and routing sensor data from distributed edge devices to central processing systems. The architecture follows an **edge-first, cloud-optional** pattern where data processing begins at the edge (Android tablet) with optional forwarding to backend systems.

### Design Principles

1. **Edge Processing First**: Filter and aggregate data at the source before transmission
2. **Lossy Tolerance**: System gracefully handles packet loss without data corruption
3. **Back-Pressure Handling**: Queuing mechanisms prevent data loss during downstream bottlenecks
4. **Schema Validation**: All incoming data validated against defined schemas
5. **Observability**: Comprehensive logging and metrics at each stage

### Key Metrics

| Metric | Target | Current |
|--------|--------|--------|
| End-to-End Latency | < 1 second | 300-800ms |
| Throughput | 1000 msgs/sec | 500-1500 msgs/sec |
| Data Loss Rate | < 0.1% | < 0.05% |
| Duplicate Rate | < 1% | < 0.5% |
| Parse Error Rate | < 0.01% | < 0.01% |

---

## Ingestion Architecture

### Three-Tier Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    TIER 1: Edge Sensors                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │ Pi Scanner 1 │  │ ESP32 Scan 2 │  │ Pi Scanner 3 │  ...        │
│  │ + BLE Stack  │  │ + BLE Stack  │  │ + BLE Stack  │             │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘             │
│         │                  │                  │                      │
│         │ MQTT Publish     │ MQTT Publish     │ MQTT Publish        │
│         │ QoS 1            │ QoS 1            │ QoS 1               │
│         └──────────────────┼──────────────────┘                     │
└────────────────────────────┼───────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  TIER 2: Ingestion Hub (Tablet)                      │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Embedded MQTT Broker (Moquette)                             │  │
│  │  - Receives from all scanners                                │  │
│  │  - Persistent sessions                                       │  │
│  │  - QoS handling                                              │  │
│  └───────────────────────┬──────────────────────────────────────┘  │
│                          │                                          │
│  ┌───────────────────────▼──────────────────────────────────────┐  │
│  │  Ingestion Processor                                         │  │
│  │  ┌────────────┐  ┌────────────┐  ┌──────────────┐          │  │
│  │  │  Receiver  │→ │ Validator  │→ │ Transformer  │          │  │
│  │  └────────────┘  └────────────┘  └──────────────┘          │  │
│  │         │               │                │                   │  │
│  │         ├───────────────┼────────────────┤                  │  │
│  │         ▼               ▼                ▼                   │  │
│  │  ┌────────────┐  ┌────────────┐  ┌──────────────┐          │  │
│  │  │   Filter   │  │ Aggregate  │  │   Enrich     │          │  │
│  │  └────────────┘  └────────────┘  └──────────────┘          │  │
│  └───────────────────────┬──────────────────────────────────────┘  │
│                          │                                          │
│  ┌───────────────────────▼──────────────────────────────────────┐  │
│  │  Data Router                                                 │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │  │
│  │  │ Local Store  │  │ MQTT Bridge  │  │  API Client  │      │  │
│  │  │  (SQLite)    │  │ (to Cloud)   │  │  (Backend)   │      │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘      │  │
│  └──────────────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   TIER 3: Backend Storage                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │   FastAPI    │  │  PostgreSQL  │  │    Redis     │             │
│  │  (Movement)  │  │  (Persistent)│  │   (Cache)    │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└─────────────────────────────────────────────────────────────────────┘
```

### Data Flow Stages

| Stage | Component | Function | Latency |
|-------|-----------|----------|--------|
| **1. Capture** | Edge Scanner | BLE advertisement capture | 10-100ms |
| **2. Serialize** | Edge Scanner | JSON encoding | 1-5ms |
| **3. Publish** | MQTT Client | Publish to broker | 5-20ms |
| **4. Broker** | Moquette | Message routing | 1-5ms |
| **5. Validate** | Ingestion Processor | Schema validation | 1-2ms |
| **6. Transform** | Ingestion Processor | Data enrichment | 2-5ms |
| **7. Filter** | Ingestion Processor | Whitelist check | 1ms |
| **8. Aggregate** | Ingestion Processor | Batch grouping | 100-5000ms |
| **9. Route** | Data Router | Store/forward decision | 1ms |
| **10. Persist** | Backend API | Database write | 10-50ms |

**Total End-to-End Latency**: 130ms - 5.2 seconds (depending on batch interval)

---

## Data Sources

### 1. Raspberry Pi Scanners

**Data Format**:
```json
{
  "scanner_id": "b8:27:eb:aa:bb:cc",
  "beacon_mac": "AC:23:3F:12:34:56",
  "rssi": -65,
  "timestamp": "2024-12-19T10:30:45.123Z",
  "tx_power": -59,
  "name": "Asset_001"
}
```

**Characteristics**:
- **Frequency**: 1-10 Hz per beacon
- **Reliability**: High (wired or stable WiFi)
- **Latency**: Low (20-50ms to broker)
- **Battery**: N/A (always powered)

**Implementation** (Python - `scanner_main.py`):
```python
async def scan_callback(device, advertisement_data):
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
```

---

### 2. ESP32 Scanners

**Data Format** (Same as Pi, but may omit `name` field)
```json
{
  "scanner_id": "24:6f:28:aa:bb:cc",
  "beacon_mac": "AC:23:3F:12:34:56",
  "rssi": -68,
  "timestamp": 123456789,
  "tx_power": null
}
```

**Characteristics**:
- **Frequency**: 0.2-5 Hz per beacon (lower due to CPU limits)
- **Reliability**: Medium (WiFi can be unstable)
- **Latency**: Medium (50-200ms to broker)
- **Battery**: 8-12 hours on 2000mAh LiPo

**Note**: ESP32 uses millis() instead of UTC timestamp

---

### 3. Android Tablet Scanner

**Data Format**:
```json
{
  "scanner_id": "tablet_abc123",
  "beacon_mac": "AC:23:3F:12:34:56",
  "rssi": -55,
  "timestamp": "2024-12-19T10:30:45.123Z",
  "tx_power": -59,
  "name": "Asset_001",
  "beacon_type": "iBeacon",
  "ibeacon_uuid": "FDA50693-A4E2-4FB1-AFCF-C6EB07647825",
  "ibeacon_major": 1,
  "ibeacon_minor": 42
}
```

**Characteristics**:
- **Frequency**: 0.2-1 Hz per beacon (batched every 5s)
- **Reliability**: High (local publish)
- **Latency**: Very low (<10ms to local broker)
- **Battery**: 24-48 hours continuous

**Implementation** (Kotlin - `BleScanner.kt`):
```kotlin
private fun deliverResults() {
    val results = currentScanResults.values.toList()
    currentScanResults.clear()
    
    results.forEach { beaconData ->
        val payload = PayloadBuilder.buildBeaconPayload(beaconData)
        mqttManager.publish(payload)
    }
}
```

---

### 4. Heartbeat Messages

**Data Format**:
```json
{
  "scanner_id": "b8:27:eb:aa:bb:cc",
  "status": "ok",
  "uptime": 86400,
  "timestamp": "2024-12-19T10:30:45Z",
  "wifi_rssi": -45,
  "beacons_detected": 23,
  "mqtt_published": 1543,
  "memory_usage": 45.2,
  "cpu_temp": 52.3
}
```

**Characteristics**:
- **Frequency**: Every 60 seconds
- **Purpose**: Health monitoring and presence detection
- **Topic**: `blex/heartbeat/{scanner_id}`

---

## Data Pipeline

### Stage 1: Reception (MQTT Broker)

**Component**: Moquette MQTT Broker

**Responsibilities**:
1. Accept TCP connections from scanners
2. Authenticate clients (username/password)
3. Handle QoS 1 acknowledgments
4. Maintain persistent sessions
5. Route messages to subscribers

**Configuration** (`EmbeddedBroker.kt`):
```kotlin
val props = Properties().apply {
    setProperty("host", "0.0.0.0")
    setProperty("port", "1883")
    setProperty("allow_anonymous", "false")
    setProperty("password_file", "$storePath/password_file.conf")
    setProperty("persistent_store", "$storePath/moquette_store.mapdb")
    setProperty("netty.mqtt.message_size", "8192") // Max message size
}

server = Server()
server.startServer(MemoryConfig(props))
```

**Metrics**:
- Messages received/sec
- Active connections
- Queue depth
- Bytes received/sec

---

### Stage 2: Subscription (Bridge Component)

**Component**: MQTT Bridge

**Responsibilities**:
1. Subscribe to all scan topics (`blex/scans/#`)
2. Subscribe to heartbeat topics (`blex/heartbeat/#`)
3. Buffer messages in memory
4. Forward to downstream processors

**Implementation** (`MqttBridge.kt`):
```kotlin
class MqttBridge(private val context: Context) {
    private val localClient = MqttAsyncClient("tcp://127.0.0.1:1883", "bridge-local")
    private val remoteClient = MqttAsyncClient(settings.remoteBrokerUrl, "bridge-remote")
    private val messageQueue = ConcurrentLinkedQueue<Message>()
    
    fun start() {
        localClient.connect()
        localClient.subscribe("blex/scans/#", 1) { topic, message ->
            processMessage(topic, message)
        }
        localClient.subscribe("blex/heartbeat/#", 1) { topic, message ->
            processHeartbeat(topic, message)
        }
    }
    
    private fun processMessage(topic: String, message: MqttMessage) {
        scope.launch {
            try {
                val payload = String(message.payload)
                val validated = validateAndTransform(payload)
                
                if (validated != null) {
                    forwardToRemote(topic, validated)
                }
            } catch (e: Exception) {
                log(LogLevel.ERROR, "Message processing failed: ${e.message}")
            }
        }
    }
}
```

---

### Stage 3: Validation

**Component**: Data Validator

**Validation Rules**:

| Field | Type | Validation |
|-------|------|------------|
| `scanner_id` | String | Non-empty, valid MAC format |
| `beacon_mac` | String | Non-empty, valid MAC format |
| `rssi` | Integer | -100 to -30 dBm |
| `timestamp` | String/Integer | Valid ISO8601 or Unix epoch |
| `tx_power` | Integer (opt) | -127 to 128 dBm |
| `name` | String (opt) | Max 50 characters |

**Implementation**:
```kotlin
data class BeaconScanSchema(
    val scanner_id: String,
    val beacon_mac: String,
    val rssi: Int,
    val timestamp: String,
    val tx_power: Int? = null,
    val name: String? = null
)

fun validateScanData(json: String): BeaconScanSchema? {
    return try {
        val data = gson.fromJson(json, BeaconScanSchema::class.java)
        
        // Validate MAC format
        if (!isMacAddress(data.scanner_id) || !isMacAddress(data.beacon_mac)) {
            log(LogLevel.WARN, "Invalid MAC address format")
            return null
        }
        
        // Validate RSSI range
        if (data.rssi < -100 || data.rssi > -30) {
            log(LogLevel.WARN, "RSSI out of range: ${data.rssi}")
            return null
        }
        
        data
    } catch (e: JsonSyntaxException) {
        log(LogLevel.ERROR, "JSON parse error: ${e.message}")
        null
    }
}

fun isMacAddress(mac: String): Boolean {
    return mac.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))
}
```

**Metrics**:
- Validation pass rate
- Validation fail rate (by error type)
- Parse errors

---

### Stage 4: Transformation

**Component**: Data Transformer

**Transformations Applied**:

1. **Timestamp Normalization**:
   - ESP32: `millis()` → ISO8601 UTC
   - Pi/Tablet: Already ISO8601

2. **Device ID Enrichment**:
   - Add device type (pi/esp32/tablet)
   - Add friendly name (from registry)

3. **Zone Mapping**:
   - Look up scanner's assigned zone
   - Add `zone_id` field

4. **RSSI Smoothing** (optional):
   - Apply Kalman filter or moving average
   - Reduce signal noise

**Implementation**:
```kotlin
fun transformBeaconData(raw: BeaconScanSchema): EnrichedBeaconData {
    // Normalize timestamp
    val timestamp = if (raw.timestamp.toLongOrNull() != null) {
        // ESP32 millis() - convert to ISO8601
        Instant.ofEpochMilli(raw.timestamp.toLong()).toString()
    } else {
        raw.timestamp // Already ISO8601
    }
    
    // Look up zone
    val scanner = ScanRepository.getScannerByMac(raw.scanner_id)
    val zoneId = scanner?.zoneId
    
    // Apply RSSI filter (optional)
    val smoothedRssi = if (settings.rssiFilterEnabled) {
        kalmanFilter.filter(raw.beacon_mac, raw.rssi.toDouble()).toInt()
    } else {
        raw.rssi
    }
    
    return EnrichedBeaconData(
        scannerId = raw.scanner_id,
        scannerType = scanner?.type ?: "unknown",
        beaconMac = raw.beacon_mac,
        rssi = smoothedRssi,
        timestamp = timestamp,
        zoneId = zoneId,
        txPower = raw.tx_power,
        name = raw.name
    )
}
```

---

### Stage 5: Filtering

**Component**: Data Filter

**Filtering Rules**:

1. **Asset Whitelist**:
   - Only process beacons registered in `mst_asset` table
   - Ignore unknown MAC addresses

2. **RSSI Threshold**:
   - Discard signals below -85 dBm (too weak)
   - Configurable threshold

3. **Duplicate Detection**:
   - If same beacon from same scanner within 1 second → discard
   - Reduces redundant data

4. **Scanner Health Check**:
   - Ignore data from scanners with stale heartbeat (>5 minutes)

**Implementation**:
```kotlin
fun filterBeaconData(data: EnrichedBeaconData): Boolean {
    // Check asset whitelist
    val isRegistered = ScanRepository.isAssetRegistered(data.beaconMac)
    if (!isRegistered) {
        log(LogLevel.DEBUG, "Beacon not registered: ${data.beaconMac}")
        return false
    }
    
    // Check RSSI threshold
    if (data.rssi < settings.rssiThreshold) {
        log(LogLevel.DEBUG, "RSSI below threshold: ${data.rssi}")
        return false
    }
    
    // Check duplicate
    val lastSeen = lastSeenCache.get("${data.scannerId}_${data.beaconMac}")
    val now = System.currentTimeMillis()
    if (lastSeen != null && (now - lastSeen) < 1000) {
        return false // Duplicate within 1 second
    }
    lastSeenCache.put("${data.scannerId}_${data.beaconMac}", now)
    
    // Check scanner health
    val scannerHealthy = ScanRepository.isScannerHealthy(data.scannerId)
    if (!scannerHealthy) {
        log(LogLevel.WARN, "Scanner unhealthy: ${data.scannerId}")
        return false
    }
    
    return true
}
```

**Metrics**:
- Total messages received
- Filtered out (by reason)
- Pass-through rate

---

### Stage 6: Aggregation

**Component**: Data Aggregator

**Aggregation Strategy**:

1. **Batching by Time Window**:
   - Collect all scan results for N seconds (default 5s)
   - Group by beacon MAC
   - Select strongest RSSI per beacon

2. **Batching by Count**:
   - Collect up to M messages (default 100)
   - Publish batch when count reached

3. **Zone Determination**:
   - For each beacon, find scanner with strongest RSSI
   - Assign beacon to that scanner's zone

**Implementation**:
```kotlin
class DataAggregator {
    private val batchWindow = 5000L // 5 seconds
    private val currentBatch = ConcurrentHashMap<String, MutableList<EnrichedBeaconData>>()
    
    fun addToBatch(data: EnrichedBeaconData) {
        val beaconKey = data.beaconMac
        currentBatch.computeIfAbsent(beaconKey) { mutableListOf() }.add(data)
    }
    
    fun processBatch() {
        val aggregated = currentBatch.map { (mac, dataList) ->
            // Find strongest RSSI
            val strongest = dataList.maxByOrNull { it.rssi }
            
            AggregatedBeaconData(
                beaconMac = mac,
                currentZoneId = strongest?.zoneId,
                decidingScannerId = strongest?.scannerId,
                decidingRssi = strongest?.rssi,
                lastSeen = strongest?.timestamp,
                sampleCount = dataList.size
            )
        }
        
        currentBatch.clear()
        
        // Emit aggregated data
        aggregated.forEach { emitAggregatedData(it) }
    }
}

// Start periodic batch processing
scope.launch {
    while (isActive) {
        delay(batchWindow)
        aggregator.processBatch()
    }
}
```

---

### Stage 7: Zone Transition Detection

**Component**: Movement Detector

**Algorithm**:
```
FOR each aggregated beacon:
    previous_zone = get_from_cache(beacon_mac)
    current_zone = aggregated_data.zone_id
    
    IF current_zone != previous_zone:
        IF dwell_time > threshold (default 10s):
            EMIT MovementEvent {
                beacon_mac: beacon_mac,
                from_zone: previous_zone,
                to_zone: current_zone,
                deciding_rssi: aggregated_data.deciding_rssi,
                timestamp: now()
            }
            
            UPDATE cache(beacon_mac, current_zone)
```

**Implementation**:
```kotlin
class MovementDetector {
    private val zoneCache = ConcurrentHashMap<String, ZoneState>()
    private val dwellTimeThreshold = 10_000L // 10 seconds
    
    data class ZoneState(
        val zoneId: Int?,
        val entryTime: Long
    )
    
    fun detectMovement(data: AggregatedBeaconData): MovementEvent? {
        val currentState = zoneCache[data.beaconMac]
        val now = System.currentTimeMillis()
        
        if (currentState == null) {
            // First time seeing this beacon
            zoneCache[data.beaconMac] = ZoneState(data.currentZoneId, now)
            return null
        }
        
        if (currentState.zoneId == data.currentZoneId) {
            // Same zone, no movement
            return null
        }
        
        // Zone changed
        val dwellTime = now - currentState.entryTime
        if (dwellTime < dwellTimeThreshold) {
            // Not enough dwell time, ignore transient signal
            return null
        }
        
        // Valid movement detected
        val event = MovementEvent(
            beaconMac = data.beaconMac,
            fromZoneId = currentState.zoneId,
            toZoneId = data.currentZoneId,
            decidingRssi = data.decidingRssi,
            timestamp = Instant.now().toString()
        )
        
        // Update cache
        zoneCache[data.beaconMac] = ZoneState(data.currentZoneId, now)
        
        return event
    }
}
```

---

## Data Validation

### Schema Definitions

**Beacon Scan Schema** (JSON Schema):
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["scanner_id", "beacon_mac", "rssi", "timestamp"],
  "properties": {
    "scanner_id": {
      "type": "string",
      "pattern": "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"
    },
    "beacon_mac": {
      "type": "string",
      "pattern": "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"
    },
    "rssi": {
      "type": "integer",
      "minimum": -100,
      "maximum": -30
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    },
    "tx_power": {
      "type": ["integer", "null"],
      "minimum": -127,
      "maximum": 128
    },
    "name": {
      "type": ["string", "null"],
      "maxLength": 50
    }
  }
}
```

### Validation Error Handling

**Error Types**:
1. **Parse Error**: Invalid JSON syntax
2. **Schema Error**: Missing required field
3. **Format Error**: Invalid MAC/timestamp format
4. **Range Error**: RSSI/tx_power out of bounds

**Response Strategy**:
- **Log**: Record error to system logs
- **Metric**: Increment error counter (by type)
- **Discard**: Drop invalid message
- **Alert**: Notify if error rate exceeds threshold (>1%)

---

## Buffering and Queueing

### Message Queue Architecture

```
┌──────────────────────────────────────────────────────┐
│         Ingestion Message Queue                      │
│                                                      │
│  Producer         Queue            Consumer          │
│  (MQTT Broker) → [Buffer] → (Processing Engine)     │
│                                                      │
│  Properties:                                         │
│  - Type: ConcurrentLinkedQueue                      │
│  - Max Size: 1000 messages                          │
│  - Overflow: Drop oldest (FIFO)                     │
│  - Persistence: In-memory only                      │
└──────────────────────────────────────────────────────┘
```

**Implementation**:
```kotlin
class MessageQueue {
    private val queue = ConcurrentLinkedQueue<Message>()
    private val maxSize = 1000
    
    fun enqueue(message: Message): Boolean {
        if (queue.size >= maxSize) {
            queue.poll() // Drop oldest
            log(LogLevel.WARN, "Queue full, dropping oldest message")
        }
        return queue.offer(message)
    }
    
    fun dequeue(): Message? {
        return queue.poll()
    }
    
    fun size(): Int = queue.size
}
```

### Back-Pressure Handling

**Scenario**: Downstream processing slower than ingestion rate

**Strategy**:
1. **Queue Depth Monitoring**: Check queue size every second
2. **Threshold Alert**: If queue > 80% full, emit warning
3. **Rate Limiting**: Slow down MQTT publish rate on scanners (future)
4. **Overflow Drop**: Drop oldest messages to prevent memory exhaustion

**Metrics**:
- Queue depth (current/max)
- Enqueue rate (msgs/sec)
- Dequeue rate (msgs/sec)
- Dropped messages (count)

---

## Performance Optimization

### Optimization Techniques

| Technique | Implementation | Impact |
|-----------|----------------|--------|
| **Batching** | Group messages every 5s | 80% reduction in API calls |
| **Filtering** | Early discard of unregistered beacons | 50% reduction in processing |
| **Caching** | In-memory zone/asset cache | 90% reduction in DB queries |
| **Async Processing** | Kotlin coroutines | 3x throughput increase |
| **JSON Parsing** | Gson with buffering | 40% faster parsing |

### Benchmarks

**Test Setup**:
- 10 scanners
- 50 beacons
- 500 messages/second ingestion rate

**Results**:

| Metric | Value |
|--------|-------|
| CPU Usage (Tablet) | 15-25% |
| Memory Usage | 120-180 MB |
| End-to-End Latency | 300-800ms |
| Messages Processed/sec | 500-1500 |
| Data Loss Rate | <0.05% |

---

## Monitoring and Observability

### Key Metrics

**Ingestion Metrics**:
```kotlin
object IngestionMetrics {
    var messagesReceived: AtomicLong = AtomicLong(0)
    var messagesValidated: AtomicLong = AtomicLong(0)
    var messagesFiltered: AtomicLong = AtomicLong(0)
    var messagesProcessed: AtomicLong = AtomicLong(0)
    var parseErrors: AtomicLong = AtomicLong(0)
    var validationErrors: AtomicLong = AtomicLong(0)
    var queueDepth: AtomicInteger = AtomicInteger(0)
    
    fun getStats(): IngestionStats {
        return IngestionStats(
            received = messagesReceived.get(),
            validated = messagesValidated.get(),
            filtered = messagesFiltered.get(),
            processed = messagesProcessed.get(),
            parseErrors = parseErrors.get(),
            validationErrors = validationErrors.get(),
            queueDepth = queueDepth.get()
        )
    }
}
```

### Logging Strategy

**Log Levels**:
- **DEBUG**: Individual message details
- **INFO**: Batch processing summaries
- **WARN**: Validation failures, queue depth alerts
- **ERROR**: Parse errors, system failures

**Log Format**:
```
[2024-12-19T10:30:45.123Z] [INFO] [Ingestion] Batch processed: 47 messages, 3 movements detected
[2024-12-19T10:30:46.234Z] [WARN] [Validation] Invalid RSSI: -105 dBm from scanner b8:27:eb:aa:bb:cc
[2024-12-19T10:30:47.345Z] [ERROR] [Parser] JSON parse error: Unexpected token at position 23
```

### Alerting Rules

| Condition | Severity | Action |
|-----------|----------|--------|
| Queue depth > 80% | Warning | Log + notify |
| Parse error rate > 1% | Warning | Log + investigate |
| Messages dropped > 10 | Error | Notification |
| No data for 5 minutes | Critical | Alert + restart |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Apr 2026 | Akshat Jain | Initial release |

**Next Review Date**: March 2025

---

*This document is part of the BleX technical documentation suite.*