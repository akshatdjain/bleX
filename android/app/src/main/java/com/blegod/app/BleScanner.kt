package com.blegod.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import com.blegod.app.data.LogLevel
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * BleScanner — scans ONLY for iBeacon and Eddystone beacons.
 *
 * Now parses iBeacon UUID/Major/Minor and Eddystone UID namespace/instance
 * from the raw advertisement data. Supports configurable scan power modes.
 */
class BleScanner(private val context: Context) {

    companion object {
        private const val TAG = "BleGod.Scanner"

        val EDDYSTONE_SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

        const val APPLE_COMPANY_ID = 0x004C
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    var isScanning = false
        private set
    private var scanJob: Job? = null
    private var restartJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val currentScanResults = ConcurrentHashMap<String, BeaconData>()

    @Volatile
    var lastScanResultTime: Long = System.currentTimeMillis()
        private set

    var onScanBatchReady: ((List<BeaconData>) -> Unit)? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> {
                        log(LogLevel.INFO, "Bluetooth ON → resuming scan")
                        startScanning()
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        log(LogLevel.WARN, "Bluetooth OFF → pausing scan")
                        stopScanning()
                    }
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val mac = device.address
            val rssi = result.rssi
            val txPower = result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }
            val name = device.name
            val scanRecord = result.scanRecord

            // Parse beacon type and extract beacon-specific data
            val parsed = parseBeacon(scanRecord)

            // Only accept identified beacons
            if (parsed == null) return

            val beaconData = BeaconData(
                mac = mac,
                rssi = rssi,
                timestamp = System.currentTimeMillis(),
                deviceId = AppConfig.getDeviceId(context),
                txPower = txPower,
                name = name ?: parsed.type,
                beaconType = parsed.type,
                ibeaconUuid = parsed.ibeaconUuid,
                ibeaconMajor = parsed.ibeaconMajor,
                ibeaconMinor = parsed.ibeaconMinor,
                eddystoneNamespace = parsed.eddystoneNamespace,
                eddystoneInstance = parsed.eddystoneInstance
            )

            currentScanResults[mac] = beaconData
            lastScanResultTime = System.currentTimeMillis()
        }

        override fun onScanFailed(errorCode: Int) {
            log(LogLevel.ERROR, "BLE scan failed with error code: $errorCode")
            scope.launch {
                delay(5000)
                log(LogLevel.INFO, "Retrying scan after failure...")
                stopScanInternal()
                delay(1000)
                startScanInternal()
            }
        }
    }

    // ── Beacon Parsing ────────────────────────────────────────────

    private data class ParsedBeacon(
        val type: String,
        val ibeaconUuid: String? = null,
        val ibeaconMajor: Int? = null,
        val ibeaconMinor: Int? = null,
        val eddystoneNamespace: String? = null,
        val eddystoneInstance: String? = null
    )

    /**
     * Parse scan record to extract beacon type and detailed data.
     */
    private fun parseBeacon(scanRecord: android.bluetooth.le.ScanRecord?): ParsedBeacon? {
        if (scanRecord == null) return null

        // ── Check for iBeacon first ──────────────────────────────
        val appleData = scanRecord.getManufacturerSpecificData(APPLE_COMPANY_ID)
        if (appleData != null && appleData.size >= 23) {
            if (appleData[0] == 0x02.toByte() && appleData[1] == 0x15.toByte()) {
                // Parse UUID (bytes 2-17, 16 bytes)
                val uuid = buildString {
                    for (i in 2..17) {
                        append(String.format("%02X", appleData[i]))
                        if (i == 5 || i == 7 || i == 9 || i == 11) append("-")
                    }
                }
                // Parse Major (bytes 18-19, big-endian)
                val major = ((appleData[18].toInt() and 0xFF) shl 8) or
                        (appleData[19].toInt() and 0xFF)
                // Parse Minor (bytes 20-21, big-endian)
                val minor = ((appleData[20].toInt() and 0xFF) shl 8) or
                        (appleData[21].toInt() and 0xFF)

                return ParsedBeacon(
                    type = "iBeacon",
                    ibeaconUuid = uuid,
                    ibeaconMajor = major,
                    ibeaconMinor = minor
                )
            }
        }

        // ── Check for Eddystone ──────────────────────────────────
        val serviceUuids = scanRecord.serviceUuids
        if (serviceUuids != null) {
            for (uuid in serviceUuids) {
                if (uuid == EDDYSTONE_SERVICE_UUID) {
                    val serviceData = scanRecord.getServiceData(EDDYSTONE_SERVICE_UUID)
                    return parseEddystone(serviceData)
                }
            }
        }

        return null
    }

    /**
     * Parse Eddystone service data.
     * Frame type 0x00 = UID frame → extract namespace (10 bytes) + instance (6 bytes)
     */
    private fun parseEddystone(data: ByteArray?): ParsedBeacon {
        if (data == null || data.isEmpty()) {
            return ParsedBeacon(type = "Eddystone")
        }

        val frameType = data[0].toInt() and 0xFF

        return when (frameType) {
            0x00 -> {
                // Eddystone-UID: [frameType(1)] [txPower(1)] [namespace(10)] [instance(6)]
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

    // ── Scanning Lifecycle ────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (isScanning) {
            Log.w(TAG, "Already scanning, ignoring start request")
            return
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            log(LogLevel.ERROR, "BluetoothLeScanner is null — is Bluetooth enabled?")
            return
        }

        isScanning = true
        lastScanResultTime = System.currentTimeMillis()

        val settings = SettingsManager.getInstance(context)
        val powerModeLabel = settings.scanPowerMode

        log(LogLevel.INFO, "BLE Scanning STARTED (iBeacon + Eddystone only)")
        log(LogLevel.INFO, "Mode: $powerModeLabel, Interval: ${AppConfig.SCAN_INTERVAL_MS}ms, Duration: ${AppConfig.SCAN_DURATION_MS}ms")

        scanJob = scope.launch {
            while (isActive) {
                try {
                    startScanInternal()
                    delay(AppConfig.SCAN_DURATION_MS)
                    stopScanInternal()
                    deliverResults()

                    val waitTime = AppConfig.SCAN_INTERVAL_MS - AppConfig.SCAN_DURATION_MS
                    if (waitTime > 0) delay(waitTime)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(LogLevel.ERROR, "Scan cycle error: ${e.message}")
                    delay(3000)
                }
            }
        }

        restartJob = scope.launch {
            while (isActive) {
                delay(AppConfig.SCAN_RESTART_INTERVAL_MINUTES * 60 * 1000)
                log(LogLevel.INFO, "Periodic scan restart (every ${AppConfig.SCAN_RESTART_INTERVAL_MINUTES} min)")
                stopScanInternal()
                delay(500)
                startScanInternal()
            }
        }
    }

    fun stopScanning() {
        isScanning = false
        scanJob?.cancel()
        restartJob?.cancel()
        stopScanInternal()
        try { context.unregisterReceiver(bluetoothStateReceiver) } catch (_: Exception) {}
        log(LogLevel.INFO, "BLE Scanning STOPPED")
    }

    fun forceRestart() {
        log(LogLevel.WARN, "Force restarting BLE scanner")
        stopScanning()
        scope.launch {
            delay(2000)
            startScanning()
        }
    }

    fun destroy() {
        stopScanning()
        scope.cancel()
    }

    // ── Internal scan control ─────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startScanInternal() {
        try {
            val settings = SettingsManager.getInstance(context)
            val scanMode = when (settings.scanPowerMode) {
                "LOW_POWER" -> ScanSettings.SCAN_MODE_LOW_POWER
                "LOW_LATENCY" -> ScanSettings.SCAN_MODE_LOW_LATENCY
                else -> ScanSettings.SCAN_MODE_BALANCED
            }

            val scanSettings = ScanSettings.Builder()
                .setScanMode(scanMode)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setReportDelay(0)
                .build()

            val filters = buildBeaconFilters()
            bluetoothLeScanner?.startScan(filters, scanSettings, scanCallback)
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Failed to start scan: ${e.message}")
        }
    }

    private fun buildBeaconFilters(): List<ScanFilter> {
        val filters = mutableListOf<ScanFilter>()

        // Filter 1: Eddystone — match by service UUID 0xFEAA
        filters.add(
            ScanFilter.Builder()
                .setServiceUuid(EDDYSTONE_SERVICE_UUID)
                .build()
        )

        // Filter 2: iBeacon — match by Apple manufacturer data prefix
        filters.add(
            ScanFilter.Builder()
                .setManufacturerData(
                    APPLE_COMPANY_ID,
                    byteArrayOf(0x02, 0x15),
                    byteArrayOf(0xFF.toByte(), 0xFF.toByte())
                )
                .build()
        )

        return filters
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scan: ${e.message}", e)
        }
    }

    private fun deliverResults() {
        lastScanResultTime = System.currentTimeMillis()

        if (currentScanResults.isEmpty()) return
        val results = currentScanResults.values.toList()
        currentScanResults.clear()
        log(LogLevel.DEBUG, "Scan cycle: ${results.size} beacons found")
        onScanBatchReady?.invoke(results)
    }

    private fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
        ScanRepository.addLog(level, "Scanner", message)
    }
}
