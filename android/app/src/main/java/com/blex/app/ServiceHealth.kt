package com.blegod.app

import android.content.Context
import com.blegod.app.data.ScanRepository

/**
 * ServiceHealth — Exposes a health report for the running service.
 *
 * Currently internal-only (shown in Settings screen).
 * Later: can be exposed via an HTTP endpoint for remote monitoring.
 */
object ServiceHealth {

    data class HealthReport(
        val uptimeMs: Long,
        val isScanning: Boolean,
        val isMqttConnected: Boolean,
        val scanCycleCount: Long,
        val totalBeaconsScanned: Long,
        val messagesPublished: Long,
        val messagesFailed: Long,
        val batteryLevel: Int,
        val isCharging: Boolean,
        val batteryTemp: Float,
        val memoryUsedMb: Long,
        val memoryTotalMb: Long
    )

    fun getReport(context: Context): HealthReport {
        val status = ScanRepository.serviceStatus.value
        val battery = BatteryMonitor.getStats(context)
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val totalMb = runtime.maxMemory() / (1024 * 1024)

        return HealthReport(
            uptimeMs = System.currentTimeMillis() - status.startTime,
            isScanning = status.isScanning,
            isMqttConnected = status.isMqttConnected,
            scanCycleCount = status.scanCycleCount,
            totalBeaconsScanned = status.totalBeaconsScanned,
            messagesPublished = status.messagesPublished,
            messagesFailed = status.messagesFailed,
            batteryLevel = battery.level,
            isCharging = battery.isCharging,
            batteryTemp = battery.temperature,
            memoryUsedMb = usedMb,
            memoryTotalMb = totalMb
        )
    }

    fun toJson(context: Context): String {
        val report = getReport(context)
        val json = org.json.JSONObject()
        json.put("uptime_ms", report.uptimeMs)
        json.put("uptime_human", formatUptime(report.uptimeMs))
        json.put("is_scanning", report.isScanning)
        json.put("is_mqtt_connected", report.isMqttConnected)
        json.put("scan_cycle_count", report.scanCycleCount)
        json.put("total_beacons_scanned", report.totalBeaconsScanned)
        json.put("messages_published", report.messagesPublished)
        json.put("messages_failed", report.messagesFailed)
        json.put("battery_level", report.batteryLevel)
        json.put("is_charging", report.isCharging)
        json.put("battery_temp_c", report.batteryTemp)
        json.put("memory_used_mb", report.memoryUsedMb)
        json.put("memory_total_mb", report.memoryTotalMb)
        return json.toString(2)
    }

    fun formatUptime(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1000
        return "${hours}h ${minutes}m ${seconds}s"
    }
}
