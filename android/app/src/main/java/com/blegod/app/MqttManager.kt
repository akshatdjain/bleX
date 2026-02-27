package com.blegod.app

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.blegod.app.data.LogLevel
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocketFactory

/**
 * MqttManager — Handles MQTT connection, publishing, and reconnection.
 *
 * CRITICAL FIX: This version prevents reconnection storms by:
 * 1. Reusing a single MqttAsyncClient instance (never recreating unless null)
 * 2. Using an AtomicBoolean `isConnecting` guard to prevent concurrent connect attempts
 * 3. Relying on Paho's built-in auto-reconnect instead of manual reconnect loops
 * 4. Only calling connect() when the client is truly null or disconnected
 */
class MqttManager(private val context: Context) {

    companion object {
        private const val TAG = "BleGod.MQTT"
    }

    private var mqttClient: MqttAsyncClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings = SettingsManager.getInstance(context)

    // Connection guards — prevent reconnection storms
    private val isConnecting = AtomicBoolean(false)

    @Volatile var isConnected = false
        private set

    private val messageQueue = ConcurrentLinkedQueue<Pair<String, String>>()

    @Volatile var totalMessagesPublished: Long = 0
        private set
    @Volatile var totalMessagesFailed: Long = 0
        private set

    private val mqttCallback = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String) {
            isConnected = true
            isConnecting.set(false)
            val type = if (reconnect) "RECONNECTED" else "CONNECTED"
            log(LogLevel.INFO, "MQTT $type to $serverURI")
            flushQueue()
        }

        override fun connectionLost(cause: Throwable?) {
            isConnected = false
            isConnecting.set(false)
            log(LogLevel.WARN, "MQTT connection lost: ${cause?.message}")
            fireDisconnectAlert(cause?.message)
            // Paho auto-reconnect will handle this — do NOT call connect() here
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {}
        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    }

    /**
     * Fire a high-priority notification when MQTT disconnects.
     */
    private fun fireDisconnectAlert(reason: String?) {
        try {
            val notification = NotificationCompat.Builder(context, BleGodApp.ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("MQTT Disconnected")
                .setContentText(reason ?: "Connection to broker lost")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(AppConfig.NOTIFICATION_ID + 1, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show disconnect alert: ${e.message}")
        }
    }

    /**
     * Connect to the MQTT broker.
     * Guards against multiple concurrent connection attempts.
     * Reuses the existing client if possible.
     */
    fun connect() {
        // Guard: prevent concurrent connection attempts
        if (!isConnecting.compareAndSet(false, true)) {
            Log.d(TAG, "Connection already in progress, skipping")
            return
        }

        // If already connected, nothing to do
        if (mqttClient?.isConnected == true) {
            isConnected = true
            isConnecting.set(false)
            return
        }

        try {
            val brokerUrl = settings.getBrokerUrl()
            val clientId = "blegod_${AppConfig.getDeviceId(context)}"

            // Only create a new client if we don't have one
            if (mqttClient == null) {
                mqttClient = MqttAsyncClient(brokerUrl, clientId, MemoryPersistence())
                mqttClient?.setCallback(mqttCallback)
                log(LogLevel.INFO, "Created new MQTT client")
            }

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = false
                keepAliveInterval = settings.mqttKeepAlive
                connectionTimeout = settings.mqttConnectionTimeout
                maxInflight = 100

                // Only use MQTT section credentials/TLS when going direct (broker disabled)
                if (!settings.brokerEnabled) {
                    val user = settings.mqttUsername
                    val pass = settings.mqttPassword
                    if (user.isNotBlank()) {
                        userName = user
                        password = pass.toCharArray()
                    }

                    if (settings.mqttTlsEnabled) {
                        socketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    }
                } else if (settings.brokerUsername.isNotEmpty()) {
                    // Use broker credentials for local connection
                    userName = settings.brokerUsername
                    password = settings.brokerPassword.toCharArray()
                }
            }

            log(LogLevel.INFO, "Connecting to $brokerUrl as $clientId")

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    // connectComplete callback will handle setting flags
                    log(LogLevel.INFO, "MQTT connect initiated successfully")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnecting.set(false)
                    log(LogLevel.ERROR, "MQTT connect failed: ${exception?.message}")
                    // Paho auto-reconnect will retry — do NOT call connect() again here
                }
            })

        } catch (e: Exception) {
            isConnecting.set(false)
            log(LogLevel.ERROR, "Failed to create/connect MQTT client: ${e.message}")
        }
    }

    /**
     * Called by the watchdog — only attempts reconnect if truly disconnected
     * and no connection attempt is already in progress.
     */
    fun reconnectIfNeeded() {
        if (isConnected || isConnecting.get()) return

        // Check if the client exists and Paho's auto-reconnect is active
        val client = mqttClient
        if (client != null) {
            // Paho should be auto-reconnecting. Only force-connect if it's been
            // a long time. The watchdog fires every 60s, so this is fine.
            log(LogLevel.INFO, "Watchdog triggering reconnect")
            connect()
        } else {
            // No client at all — create one
            connect()
        }
    }

    fun publish(payload: String) {
        val topic = "${settings.mqttTopicPrefix}/${AppConfig.getDeviceId(context)}"

        if (!isConnected || mqttClient == null) {
            queueMessage(topic, payload)
            return
        }

        try {
            val message = MqttMessage(payload.toByteArray()).apply {
                qos = settings.mqttQos
                isRetained = false
            }

            mqttClient?.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    totalMessagesPublished++
                    if (totalMessagesPublished % 100 == 0L) {
                        log(LogLevel.DEBUG, "Published $totalMessagesPublished messages total")
                    }
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    totalMessagesFailed++
                    log(LogLevel.ERROR, "Publish failed: ${exception?.message}")
                    queueMessage(topic, payload)
                }
            })
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Publish exception: ${e.message}")
            queueMessage(topic, payload)
        }
    }

    fun disconnect() {
        try {
            isConnecting.set(false)
            mqttClient?.setCallback(null)
            mqttClient?.disconnect()
            mqttClient?.close()
            mqttClient = null
            isConnected = false
            scope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "MQTT disconnect error: ${e.message}", e)
        }
    }

    private fun queueMessage(topic: String, payload: String) {
        if (messageQueue.size < 1000) {
            messageQueue.add(Pair(topic, payload))
        } else {
            messageQueue.poll()
            messageQueue.add(Pair(topic, payload))
        }
    }

    private fun flushQueue() {
        if (messageQueue.isEmpty()) return
        val count = messageQueue.size
        log(LogLevel.INFO, "Flushing $count queued messages")

        scope.launch {
            while (messageQueue.isNotEmpty() && isConnected) {
                val (topic, payload) = messageQueue.poll() ?: break
                try {
                    val message = MqttMessage(payload.toByteArray()).apply {
                        qos = settings.mqttQos
                        isRetained = false
                    }
                    mqttClient?.publish(topic, message)
                    totalMessagesPublished++
                    delay(10)
                } catch (e: Exception) {
                    Log.e(TAG, "Flush failed: ${e.message}")
                    break
                }
            }
        }
    }

    private fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
        ScanRepository.addLog(level, "MQTT", message)
    }
}
