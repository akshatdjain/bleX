package com.blegod.app

import android.content.Context
import android.util.Log
import com.blegod.app.data.LogLevel
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocketFactory

/**
 * MqttBridge — Subscribes to the local embedded broker and forwards
 * all messages to the remote upstream server via WSS/TLS 1.3.
 *
 * Handles reconnection on both ends and buffers messages during
 * remote server disconnects.
 */
class MqttBridge(private val context: Context) {

    companion object {
        private const val TAG = "BleGod.Bridge"
        private const val MAX_QUEUE_SIZE = 1000
    }

    private var localClient: MqttAsyncClient? = null
    private var remoteClient: MqttAsyncClient? = null
    private val offlineQueue = ConcurrentLinkedQueue<Pair<String, String>>()
    private val isStarted = AtomicBoolean(false)

    @Volatile var isLocalConnected: Boolean = false; private set
    @Volatile var isRemoteConnected: Boolean = false; private set
    @Volatile var messagesForwarded: Long = 0; private set

    fun start() {
        if (isStarted.getAndSet(true)) return

        val settings = SettingsManager.getInstance(context)

        try {
            connectLocal(settings)
            connectRemote(settings)
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Bridge start failed: ${e.message}")
            Log.e(TAG, "Bridge start error", e)
        }
    }

    fun stop() {
        isStarted.set(false)
        try {
            localClient?.apply {
                if (isConnected) disconnect()
                close()
            }
        } catch (_: Exception) {}
        try {
            remoteClient?.apply {
                if (isConnected) disconnect()
                close()
            }
        } catch (_: Exception) {}
        localClient = null
        remoteClient = null
        isLocalConnected = false
        isRemoteConnected = false
        log(LogLevel.INFO, "Bridge stopped")
    }

    fun reconnectRemoteIfNeeded() {
        if (!isRemoteConnected && isStarted.get()) {
            try {
                val settings = SettingsManager.getInstance(context)
                connectRemote(settings)
            } catch (e: Exception) {
                log(LogLevel.ERROR, "Remote reconnect failed: ${e.message}")
            }
        }
    }

    // ── Local broker connection ──────────────────────────────────

    private fun connectLocal(settings: SettingsManager) {
        val localUrl = "tcp://127.0.0.1:${settings.brokerPort}"
        log(LogLevel.INFO, "Connecting to local broker: $localUrl")

        localClient = MqttAsyncClient(localUrl, "blegod-bridge-local", MemoryPersistence())
        localClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                isLocalConnected = true
                log(LogLevel.INFO, "Local connected (reconnect=$reconnect)")
                subscribeLocal(settings)
            }
            override fun connectionLost(cause: Throwable?) {
                isLocalConnected = false
                log(LogLevel.WARN, "Local connection lost: ${cause?.message}")
            }
            override fun messageArrived(topic: String, message: MqttMessage) {
                handleLocalMessage(topic, message)
            }
            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        val opts = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 5
            keepAliveInterval = 15
            if (settings.brokerUsername.isNotEmpty()) {
                userName = settings.brokerUsername
                password = settings.brokerPassword.toCharArray()
            }
        }

        localClient?.connect(opts)
    }

    private fun subscribeLocal(settings: SettingsManager) {
        val topicFilter = settings.bridgeTopicFilter
        log(LogLevel.INFO, "Subscribing to local: $topicFilter")
        localClient?.subscribe(topicFilter, 1)
    }

    // ── Remote server connection ─────────────────────────────────

    private fun connectRemote(settings: SettingsManager) {
        val remoteUrl = buildRemoteUrl(settings)
        log(LogLevel.INFO, "Connecting to remote: $remoteUrl")

        remoteClient = MqttAsyncClient(remoteUrl, "blegod-bridge-remote", MemoryPersistence())
        remoteClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                isRemoteConnected = true
                log(LogLevel.INFO, "Remote connected (reconnect=$reconnect)")
                flushOfflineQueue()
            }
            override fun connectionLost(cause: Throwable?) {
                isRemoteConnected = false
                log(LogLevel.WARN, "Remote connection lost: ${cause?.message}")
            }
            override fun messageArrived(topic: String, message: MqttMessage) {}
            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        val opts = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 10
            keepAliveInterval = 30
            if (settings.remoteUsername.isNotEmpty()) {
                userName = settings.remoteUsername
                password = settings.remotePassword.toCharArray()
            }
            // TLS for ssl:// or wss:// connections
            if (settings.remoteTlsEnabled) {
                socketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            }
        }

        remoteClient?.connect(opts)
    }

    private fun buildRemoteUrl(settings: SettingsManager): String {
        return when {
            settings.remoteUseWebSocket && settings.remoteTlsEnabled ->
                "wss://${settings.remoteHost}:${settings.remotePort}"
            settings.remoteUseWebSocket ->
                "ws://${settings.remoteHost}:${settings.remotePort}"
            settings.remoteTlsEnabled ->
                "ssl://${settings.remoteHost}:${settings.remotePort}"
            else ->
                "tcp://${settings.remoteHost}:${settings.remotePort}"
        }
    }

    // ── Message handling ─────────────────────────────────────────

    private fun handleLocalMessage(topic: String, message: MqttMessage) {
        val payload = String(message.payload)

        if (isRemoteConnected && remoteClient?.isConnected == true) {
            try {
                remoteClient?.publish(topic, message)
                messagesForwarded++
                log(LogLevel.DEBUG, "Forwarded: $topic (${payload.length} bytes)")
            } catch (e: Exception) {
                log(LogLevel.WARN, "Forward failed, queueing: ${e.message}")
                queueMessage(topic, payload)
            }
        } else {
            queueMessage(topic, payload)
        }
    }

    private fun queueMessage(topic: String, payload: String) {
        if (offlineQueue.size >= MAX_QUEUE_SIZE) {
            offlineQueue.poll() // Drop oldest
        }
        offlineQueue.offer(topic to payload)
    }

    private fun flushOfflineQueue() {
        var flushed = 0
        while (offlineQueue.isNotEmpty() && isRemoteConnected) {
            val (topic, payload) = offlineQueue.poll() ?: break
            try {
                val msg = MqttMessage(payload.toByteArray()).apply { qos = 1 }
                remoteClient?.publish(topic, msg)
                messagesForwarded++
                flushed++
            } catch (e: Exception) {
                log(LogLevel.WARN, "Flush failed: ${e.message}")
                break
            }
        }
        if (flushed > 0) {
            log(LogLevel.INFO, "Flushed $flushed queued messages to remote")
        }
    }

    private fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
        ScanRepository.addBrokerLog(level, "Bridge", message)
    }
}
