package com.blegod.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.blegod.app.data.LogLevel
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager
import com.blegod.app.network.UdpDiscoveryManager
import kotlinx.coroutines.*

class BleScannerService : Service() {

    companion object {
        private const val TAG = "BleGod.Service"
        const val ACTION_RESTART = "com.blegod.app.ACTION_RESTART_SERVICE"
        const val ACTION_RESPAWN_NOTIFICATION = "com.blegod.app.ACTION_RESPAWN_NOTIFICATION"
    }

    private lateinit var bleScanner: BleScanner
    private lateinit var mqttManager: MqttManager
    private lateinit var settings: SettingsManager
    private var embeddedBroker: EmbeddedBroker? = null
    private var mqttBridge: MqttBridge? = null
    private var udpDiscovery: UdpDiscoveryManager? = null

    private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        handleSettingsChange(key)
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var watchdogJob: Job? = null
    private var notificationUpdateJob: Job? = null
    private var uiUpdateJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var totalBeaconsScanned: Long = 0
    @Volatile private var scanCycleCount: Long = 0
    private var serviceStartTime: Long = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        log(LogLevel.INFO, "Service CREATED")

        settings = SettingsManager.getInstance(this)
        settings.getPrefs().registerOnSharedPreferenceChangeListener(settingsListener)

        val initRemoteStatus = if (settings.brokerEnabled && settings.remoteHost.isNotEmpty()) "Down" else "--"
        val notification = buildNotification(false, false, initRemoteStatus)
        startForeground(AppConfig.NOTIFICATION_ID, notification)
        acquireWakeLock()

        // Start embedded MQTT broker (if enabled)
        if (settings.brokerEnabled) {
            embeddedBroker = EmbeddedBroker(this)
            embeddedBroker?.start()

            // Wait for broker to be ready before connecting clients
            var waitMs = 0
            while (embeddedBroker?.isRunning != true && waitMs < 3000) {
                Thread.sleep(200)
                waitMs += 200
            }
            if (embeddedBroker?.isRunning == true) {
                log(LogLevel.INFO, "Broker ready after ${waitMs}ms")
            } else {
                log(LogLevel.ERROR, "Broker failed to start within 3s")
            }
        }

        // Initialize MQTT client (connects to local broker or remote)
        mqttManager = MqttManager(this)
        mqttManager.connect()

        // Start bridge to remote server (if broker enabled and remote configured)
        if (settings.brokerEnabled && settings.remoteHost.isNotEmpty()) {
            mqttBridge = MqttBridge(this)
            mqttBridge?.start()
        }

        // Initialize BLE scanner
        bleScanner = BleScanner(this)

        // Wire scan results → MQTT + UI
        bleScanner.onScanBatchReady = { beacons ->
            scanCycleCount++
            totalBeaconsScanned += beacons.size

            // Use PayloadBuilder for configurable MQTT payload
            val payload = PayloadBuilder.buildBatchPayload(this, beacons)
            mqttManager.publish(payload)

            // Push to UI
            ScanRepository.updateBeacons(beacons)

            log(LogLevel.DEBUG, "Cycle #$scanCycleCount: ${beacons.size} beacons → MQTT")
        }

        bleScanner.startScanning()
        startWatchdog()
        startNotificationUpdater()
        startUIUpdater()

        // Start UDP discovery for Configurator
        udpDiscovery = UdpDiscoveryManager(this)
        udpDiscovery?.start()
        log(LogLevel.INFO, "UDP discovery started on port ${UdpDiscoveryManager.UDP_PORT}")

        // Update initial status
        ScanRepository.updateServiceStatus(
            isScanning = true,
            startTime = serviceStartTime
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESPAWN_NOTIFICATION -> {
                log(LogLevel.DEBUG, "Notification swiped - respawning")
                val remoteStatus = if (mqttBridge?.isRemoteConnected == true) "Up" else if (settings.remoteHost.isEmpty()) "--" else "Down"
                
                val notification = buildNotification(
                    bleScanner.isScanning,
                    embeddedBroker?.isRunning == true,
                    remoteStatus
                )
                startForeground(AppConfig.NOTIFICATION_ID, notification)
            }
            ACTION_RESTART -> {
                log(LogLevel.INFO, "Service restarted via alarm")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        log(LogLevel.WARN, "App removed from recents — scheduling restart")
        scheduleRestart()
    }

    override fun onDestroy() {
        log(LogLevel.WARN, "Service onDestroy — cleaning up")

        ScanRepository.updateServiceStatus(isScanning = false)

        watchdogJob?.cancel()
        notificationUpdateJob?.cancel()
        uiUpdateJob?.cancel()
        serviceScope.cancel()
        bleScanner.destroy()
        mqttBridge?.stop()
        mqttManager.disconnect()
        embeddedBroker?.stop()
        releaseWakeLock()
        scheduleRestart()

        settings.getPrefs().unregisterOnSharedPreferenceChangeListener(settingsListener)
        udpDiscovery?.stop()
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────

    private var lastNotificationStatus = ""

    private fun startNotificationUpdater() {
        notificationUpdateJob = serviceScope.launch {
            // Give initial state time to settle
            delay(1000)
            while (isActive) {
                try {
                    val currentIsScanning = bleScanner.isScanning
                    val currentBrokerRunning = embeddedBroker?.isRunning == true
                    val remoteStatus = if (mqttBridge?.isRemoteConnected == true) "Up" else if (settings.remoteHost.isEmpty()) "--" else "Down"
                    val currentBeacons = totalBeaconsScanned

                    val stateKey = "$currentIsScanning|$currentBrokerRunning|$remoteStatus|$currentBeacons"

                    // ONLY update the notification if the status actually changed.
                    if (stateKey != lastNotificationStatus) {
                        lastNotificationStatus = stateKey
                        val notification = buildNotification(
                            currentIsScanning,
                            currentBrokerRunning,
                            remoteStatus
                        )
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        manager.notify(AppConfig.NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Notification update failed: ${e.message}")
                }
                delay(5_000)
            }
        }
    }

    // ── UI Status Updater ─────────────────────────────────────────

    private fun startUIUpdater() {
        uiUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(2_000)
                val battery = BatteryMonitor.getStats(this@BleScannerService)
                ScanRepository.updateServiceStatus(
                    isScanning = true,
                    isMqttConnected = mqttManager.isConnected,
                    isLocationEnabled = bleScanner.isLocationEnabled,
                    scanCycleCount = scanCycleCount,
                    totalBeaconsScanned = totalBeaconsScanned,
                    messagesPublished = mqttManager.totalMessagesPublished,
                    messagesFailed = mqttManager.totalMessagesFailed,
                    batteryLevel = battery.level,
                    isCharging = battery.isCharging,
                    startTime = serviceStartTime
                )
            }
        }
    }

    // ── Watchdog ──────────────────────────────────────────────────

    private fun startWatchdog() {
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(AppConfig.WATCHDOG_INTERVAL_MS)

                val timeSinceLastScan =
                    System.currentTimeMillis() - bleScanner.lastScanResultTime

                log(LogLevel.DEBUG, "Watchdog: lastScan=${timeSinceLastScan}ms ago, mqtt=${mqttManager.isConnected}")

                if (timeSinceLastScan > AppConfig.WATCHDOG_STALL_THRESHOLD_MS) {
                    log(LogLevel.WARN, "Watchdog: Scanner stalled! Restarting...")
                    bleScanner.forceRestart()
                }

                if (!mqttManager.isConnected) {
                    log(LogLevel.WARN, "Watchdog: MQTT disconnected. Requesting reconnect...")
                    mqttManager.reconnectIfNeeded()
                }

                // Monitor bridge remote connection
                mqttBridge?.let { bridge ->
                    if (!bridge.isRemoteConnected) {
                        bridge.reconnectRemoteIfNeeded()
                    }
                }
            }
        }
    }

    // ── Self-Healing ──────────────────────────────────────────────

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleRestart() {
        try {
            val restartIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
                action = ACTION_RESTART
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 3000,
                pendingIntent
            )
            log(LogLevel.INFO, "Restart alarm scheduled for 3s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule restart: ${e.message}", e)
        }
    }

    // ── Wake Lock ─────────────────────────────────────────────────

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BleGod::ScannerWakeLock"
        ).apply { acquire(24 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ── Logging helper ────────────────────────────────────────────

    private fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
        ScanRepository.addLog(level, "Service", message)
    }

    private var settingsChangeJob: Job? = null
    private var pendingRestartMqtt = false
    private var pendingRestartBridge = false
    private var pendingRestartScanner = false

    private fun handleSettingsChange(key: String?) {
        key ?: return
        log(LogLevel.INFO, "Settings changed: $key")

        // Accumulate pending actions
        when {
            key.startsWith("mqtt_") || key == "broker_enabled" -> {
                pendingRestartMqtt = true
                pendingRestartBridge = true
            }
            key.startsWith("remote_") || key == "bridge_topic_filter" -> {
                pendingRestartBridge = true
            }
            key.startsWith("scan_") -> {
                pendingRestartScanner = true
            }
        }

        // Debounce the actual restart
        // If 40 keys are saved instantly, this cancels and resets 40 times, 
        // waiting 1500ms after the last save before actually restarting things.
        settingsChangeJob?.cancel()
        settingsChangeJob = serviceScope.launch {
            delay(1500)

            if (pendingRestartMqtt) {
                log(LogLevel.INFO, "Debounced: Restarting MQTT/Broker")
                mqttBridge?.stop()
                mqttManager.disconnect()
                embeddedBroker?.stop()
                
                delay(1000)
                
                if (settings.brokerEnabled) {
                    embeddedBroker = EmbeddedBroker(this@BleScannerService)
                    embeddedBroker?.start()
                    delay(1000)
                }
                
                mqttManager.connect()
                
                if (settings.brokerEnabled && settings.remoteHost.isNotEmpty()) {
                    mqttBridge = MqttBridge(this@BleScannerService)
                    mqttBridge?.start()
                }
            } else if (pendingRestartBridge) {
                log(LogLevel.INFO, "Debounced: Restarting Bridge")
                mqttBridge?.stop()
                delay(500)
                if (settings.brokerEnabled && settings.remoteHost.isNotEmpty()) {
                    mqttBridge = MqttBridge(this@BleScannerService)
                    mqttBridge?.start()
                }
            }

            if (pendingRestartScanner) {
                log(LogLevel.INFO, "Debounced: Restarting Scanner")
                bleScanner.stopScanning()
                delay(500)
                bleScanner.startScanning()
            }

            // Reset flags
            pendingRestartMqtt = false
            pendingRestartBridge = false
            pendingRestartScanner = false
        }
    }

    // ── Notification Builder ──────────────────────────────────────
    private fun buildNotification(
        isScanning: Boolean,
        isMqttConnected: Boolean,
        remoteStatus: String
    ): Notification {
        val swipeIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
            action = ACTION_RESPAWN_NOTIFICATION
        }
        val swipePendingIntent = PendingIntent.getBroadcast(
            this, 0, swipeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val singleLineStatus = "BLE: ${if (isScanning) "Active" else "Inactive"} | MQTT: ${if (isMqttConnected) "Up" else "Down"} | Remote: $remoteStatus"

        val builder = NotificationCompat.Builder(this, BleGodApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Scanner Status")
            .setContentText(singleLineStatus)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setDeleteIntent(swipePendingIntent)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        val notification = builder.build()
        notification.flags = notification.flags or
                Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_NO_CLEAR or
                Notification.FLAG_FOREGROUND_SERVICE

        return notification
    }
}
