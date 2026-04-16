package com.blegod.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * BatteryMonitor — Reads battery stats from the system.
 *
 * Provides battery level, charging state, temperature, and estimated drain rate.
 */
object BatteryMonitor {

    data class BatteryStats(
        val level: Int,           // 0-100
        val isCharging: Boolean,
        val temperature: Float,   // Celsius
        val voltage: Float,       // Volts
        val health: String,
        val plugType: String
    )

    private var lastLevel: Int = -1
    private var lastLevelTime: Long = 0
    private var drainRatePerHour: Float = 0f   // % per hour

    fun getStats(context: Context): BatteryStats {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (scale > 0) (level * 100) / scale else -1

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f

        val healthInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        val pluggedInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plugType = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }

        // Track drain rate
        val now = System.currentTimeMillis()
        if (lastLevel >= 0 && !isCharging && percent < lastLevel) {
            val elapsedHours = (now - lastLevelTime) / 3_600_000f
            if (elapsedHours > 0.01f) {
                drainRatePerHour = (lastLevel - percent) / elapsedHours
            }
        }
        if (lastLevel != percent) {
            lastLevel = percent
            lastLevelTime = now
        }

        return BatteryStats(
            level = percent,
            isCharging = isCharging,
            temperature = temp,
            voltage = voltage,
            health = health,
            plugType = plugType
        )
    }

    fun getDrainRatePerHour(): Float = drainRatePerHour
}
