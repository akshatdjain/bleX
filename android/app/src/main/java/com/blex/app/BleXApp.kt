package com.blegod.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import kotlin.system.exitProcess

/**
 * BleXApp — The Application class.
 *
 * Creates notification channels and registers a global crash handler
 * to auto-restart the service on any uncaught exception.
 */
class BleXApp : Application() {

    companion object {
        const val TAG = "BleX"
        const val CHANNEL_ID = "blex_scanner_channel"
        const val CHANNEL_NAME = "BLE Scanner Service"
        const val ALERT_CHANNEL_ID = "blex_alerts_channel"
        const val ALERT_CHANNEL_NAME = "BleX Alerts"
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannels()
        registerCrashHandler()

        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "  BleX Application Started")
        Log.i(TAG, "═══════════════════════════════════════")
    }

    /**
     * Creates notification channels:
     * 1. Scanner service channel (LOW importance, silent)
     * 2. Alert channel (HIGH importance, for MQTT disconnect etc.)
     */
    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Persistent Foreground Service channel (Must be default or higher for AOD rendering)
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT // Changed from LOW to bypass AOD suppression
        ).apply {
            description = "Continuous BLE beacon scanning service"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC // CRITICAL for Android 14+ AOD
        }
        notificationManager.createNotificationChannel(serviceChannel)

        // Alert channel (MQTT disconnect, critical errors)
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            ALERT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important alerts: MQTT disconnection, errors"
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(alertChannel)

        Log.d(TAG, "Notification channels created: $CHANNEL_ID, $ALERT_CHANNEL_ID")
    }

    /**
     * Registers a global uncaught exception handler.
     * On crash: logs the error, schedules a restart alarm, and kills the process.
     */
    private fun registerCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "FATAL CRASH in ${thread.name}: ${throwable.message}", throwable)

                // Schedule service restart via alarm
                val restartIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
                    action = BleScannerService.ACTION_RESTART
                }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    this, 999, restartIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                            android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 2000,
                    pendingIntent
                )
                Log.e(TAG, "Crash restart scheduled for 2s")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule crash restart: ${e.message}")
            }

            // Let the default handler do its thing (show crash dialog or kill)
            defaultHandler?.uncaughtException(thread, throwable)
                ?: exitProcess(1)
        }

        Log.d(TAG, "Crash handler registered")
    }
}
