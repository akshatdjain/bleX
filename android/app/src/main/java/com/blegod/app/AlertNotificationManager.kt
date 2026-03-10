package com.blegod.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * AlertNotificationManager — Monitors critical conditions and fires
 * Android notifications with vibration:
 *   • Battery low (< 15%)
 *   • BLE scanning stopped
 *   • Wi-Fi / Internet disconnected
 *   • MQTT disconnected
 *   • Remote server unreachable
 *
 * Uses a cooldown to avoid notification spam.
 */
class AlertNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "blegod_alerts"
        private const val CHANNEL_NAME = "BleGod Alerts"
        private const val COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes between repeats

        // Notification IDs
        private const val NOTIF_BATTERY_LOW = 9001
        private const val NOTIF_BLE_DOWN = 9002
        private const val NOTIF_WIFI_DOWN = 9003
        private const val NOTIF_MQTT_DOWN = 9004
        private const val NOTIF_SERVER_DOWN = 9005
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val lastFireTimes = mutableMapOf<Int, Long>()

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical alerts for BleGod operation"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200) // vibrate pattern
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Call periodically from the service to check all conditions.
     */
    fun checkAndAlert(
        batteryLevel: Int,
        isCharging: Boolean,
        isBleScanning: Boolean,
        isMqttConnected: Boolean,
        isWifiConnected: Boolean
    ) {
        // Battery low
        if (batteryLevel in 1..14 && !isCharging) {
            fireAlert(
                id = NOTIF_BATTERY_LOW,
                title = "Battery Low",
                body = "Battery at $batteryLevel%. Connect charger to avoid service interruption.",
                icon = android.R.drawable.ic_dialog_alert
            )
        } else {
            dismissIfRecovered(NOTIF_BATTERY_LOW)
        }

        // BLE scanning down
        if (!isBleScanning) {
            fireAlert(
                id = NOTIF_BLE_DOWN,
                title = "BLE Scanning Stopped",
                body = "Beacon scanning has stopped. Check Location Services and Bluetooth.",
                icon = android.R.drawable.stat_notify_error
            )
        } else {
            dismissIfRecovered(NOTIF_BLE_DOWN)
        }

        // Wi-Fi / Internet
        if (!isWifiConnected) {
            fireAlert(
                id = NOTIF_WIFI_DOWN,
                title = "Network Disconnected",
                body = "Wi-Fi/Internet is down. MQTT publishing may be affected.",
                icon = android.R.drawable.stat_notify_error
            )
        } else {
            dismissIfRecovered(NOTIF_WIFI_DOWN)
        }

        // MQTT
        if (!isMqttConnected) {
            fireAlert(
                id = NOTIF_MQTT_DOWN,
                title = "MQTT Disconnected",
                body = "Lost connection to MQTT broker. Beacon data is not being published.",
                icon = android.R.drawable.stat_notify_error
            )
        } else {
            dismissIfRecovered(NOTIF_MQTT_DOWN)
        }
    }

    /**
     * Fire a notification with cooldown to avoid spam.
     */
    private fun fireAlert(id: Int, title: String, body: String, icon: Int) {
        val now = System.currentTimeMillis()
        val lastFire = lastFireTimes[id] ?: 0L
        if (now - lastFire < COOLDOWN_MS) return

        lastFireTimes[id] = now

        // Create an intent that will open MainActivity and navigate to a specific route
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Map the notification ID to a specific settings/config route
            val route = when (id) {
                NOTIF_BATTERY_LOW -> "settings" // Battery panel is in Settings
                NOTIF_BLE_DOWN -> "settings" // Beacon Discovery panel is in Settings
                NOTIF_MQTT_DOWN -> "settings" // Local/Remote MQTT is in Settings
                NOTIF_SERVER_DOWN -> "settings"
                else -> "scanner"
            }
            putExtra("navigate_to", route)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            id, // Use the notification ID as the requestCode
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // Tap to open app
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        notificationManager.notify(id, notification)

        // Vibrate
        vibrate()
    }

    private fun dismissIfRecovered(id: Int) {
        notificationManager.cancel(id)
        lastFireTimes.remove(id)
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) { /* ignore vibration errors */ }
    }
}
