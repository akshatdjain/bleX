package com.blegod.app.data

import com.blegod.app.BeaconData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.blegod.app.data.ApiService

/**
 * ScanRepository — The bridge between background service and UI.
 *
 * The BleScannerService pushes data HERE.
 * The Compose UI observes data FROM HERE via StateFlows.
 *
 * Using a singleton so both the Service and Activity can access it
 * without needing any binding or IPC.
 */
object ScanRepository {

    // ── Beacon Data ──────────────────────────────────────────────
    private val _beacons = MutableStateFlow<List<BeaconData>>(emptyList())
    val beacons: StateFlow<List<BeaconData>> = _beacons.asStateFlow()

    // ── Service Status ───────────────────────────────────────────
    private val _serviceStatus = MutableStateFlow(ServiceStatus())
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    // ── Discovered Scanners (Provisioning) ───────────────────────
    private val _discoveredScanners = MutableStateFlow<Map<String, DiscoveredScanner>>(emptyMap())
    
    // Using a manual update to keep it simple and avoid complex flow dependencies
    private val _discoveredScannersList = MutableStateFlow<List<DiscoveredScanner>>(emptyList())
    val discoveredScanners: StateFlow<List<DiscoveredScanner>> = _discoveredScannersList.asStateFlow()

    // ── Logs (Scanner) ────────────────────────────────────────────
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    private val logBuffer = mutableListOf<LogEntry>()
    private const val MAX_LOGS = 500

    // ── Logs (Broker) ────────────────────────────────────────────
    private val _brokerLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val brokerLogs: StateFlow<List<LogEntry>> = _brokerLogs.asStateFlow()
    private val brokerLogBuffer = mutableListOf<LogEntry>()

    // ── Zones ─────────────────────────────────────────────────────
    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    // ── Registered Assets (name lookup by MAC) ───────────────────
    private val _registeredAssets = MutableStateFlow<Map<String, ApiService.ApiAsset>>(emptyMap())
    val registeredAssets: StateFlow<Map<String, ApiService.ApiAsset>> = _registeredAssets.asStateFlow()

    // ══════════════════════════════════════════════════════════════
    //  Service → Repository (called by BleScannerService)
    // ══════════════════════════════════════════════════════════════

    fun updateBeacons(beacons: List<BeaconData>) {
        _beacons.value = beacons.sortedByDescending { it.rssi }
    }

    fun addOrUpdateScanner(scanner: DiscoveredScanner) {
        val current = _discoveredScanners.value.toMutableMap()
        current[scanner.mac] = scanner
        _discoveredScanners.value = current
        _discoveredScannersList.value = current.values.sortedBy { it.name }
    }

    fun removeStaleScanners(macs: Set<String>) {
        val current = _discoveredScanners.value.toMutableMap()
        macs.forEach { current.remove(it) }
        _discoveredScanners.value = current
        _discoveredScannersList.value = current.values.sortedBy { it.name }
    }

    fun updateServiceStatus(
        isScanning: Boolean = _serviceStatus.value.isScanning,
        isMqttConnected: Boolean = _serviceStatus.value.isMqttConnected,
        scanCycleCount: Long = _serviceStatus.value.scanCycleCount,
        totalBeaconsScanned: Long = _serviceStatus.value.totalBeaconsScanned,
        messagesPublished: Long = _serviceStatus.value.messagesPublished,
        messagesFailed: Long = _serviceStatus.value.messagesFailed,
        batteryLevel: Int = _serviceStatus.value.batteryLevel,
        isCharging: Boolean = _serviceStatus.value.isCharging,
        startTime: Long = _serviceStatus.value.startTime
    ) {
        _serviceStatus.value = ServiceStatus(
            isScanning = isScanning,
            isMqttConnected = isMqttConnected,
            scanCycleCount = scanCycleCount,
            totalBeaconsScanned = totalBeaconsScanned,
            messagesPublished = messagesPublished,
            messagesFailed = messagesFailed,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            startTime = startTime
        )
    }

    fun addLog(level: LogLevel, tag: String, message: String) {
        synchronized(logBuffer) {
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message
            )
            logBuffer.add(entry)
            if (logBuffer.size > MAX_LOGS) {
                logBuffer.removeAt(0)
            }
            _logs.value = logBuffer.toList()
        }
    }

    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
            _logs.value = emptyList()
        }
    }

    fun addBrokerLog(level: LogLevel, tag: String, message: String) {
        synchronized(brokerLogBuffer) {
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message
            )
            brokerLogBuffer.add(entry)
            if (brokerLogBuffer.size > MAX_LOGS) {
                brokerLogBuffer.removeAt(0)
            }
            _brokerLogs.value = brokerLogBuffer.toList()
        }
    }

    fun clearBrokerLogs() {
        synchronized(brokerLogBuffer) {
            brokerLogBuffer.clear()
            _brokerLogs.value = emptyList()
        }
    }

    // ── Zones ─────────────────────────────────────────────────────

    fun setZones(zones: List<Zone>) {
        _zones.value = zones
    }

    fun addZone(zone: Zone) {
        _zones.value = _zones.value + zone
    }

    fun removeZone(id: String) {
        _zones.value = _zones.value.filter { it.id != id }
    }

    fun assignScanner(zoneId: String, mac: String) {
        _zones.value = _zones.value.map {
            if (it.id == zoneId) it.copy(assignedScanners = it.assignedScanners + mac) else it
        }
    }

    fun unassignScanner(zoneId: String, mac: String) {
        _zones.value = _zones.value.map {
            if (it.id == zoneId) it.copy(assignedScanners = it.assignedScanners - mac) else it
        }
    }

    // ── Registered Assets ────────────────────────────────────────

    fun setRegisteredAssets(assets: List<ApiService.ApiAsset>) {
        _registeredAssets.value = assets.associateBy { it.bluetoothId.uppercase() }
    }

    fun getAssetName(mac: String): String? {
        return _registeredAssets.value[mac.uppercase()]?.assetName
    }
}

data class ServiceStatus(
    val isScanning: Boolean = false,
    val isMqttConnected: Boolean = false,
    val scanCycleCount: Long = 0,
    val totalBeaconsScanned: Long = 0,
    val messagesPublished: Long = 0,
    val messagesFailed: Long = 0,
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val startTime: Long = System.currentTimeMillis()
)

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class DiscoveredScanner(
    val name: String,
    val mac: String,
    val ip: String,
    val type: String,
    val lastSeenMs: Long = System.currentTimeMillis(),
    val status: String = "Discovered"
)

data class Zone(
    val id: String,
    val name: String,
    val assignedScanners: List<String> = emptyList()
)
