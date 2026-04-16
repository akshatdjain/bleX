package com.blegod.app

import android.content.Context
import android.util.Log
import com.blegod.app.data.LogLevel
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.security.cert.X509Certificate
import javax.net.ssl.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MqttBridge — Subscribes to the local embedded broker and forwards
 * all messages to the remote upstream server via WSS/TLS 1.3.
 *
 * Handles reconnection on both ends and buffers messages during
 * remote server disconnects.
 */
class MqttBridge(private val context: Context) {

    companion object {
        private const val TAG = "BleX.Bridge"
        private const val MAX_QUEUE_SIZE = 1000
    }

    private var localClient: MqttAsyncClient? = null
    private var remoteClient: MqttAsyncClient? = null
    private val offlineQueue = ConcurrentLinkedQueue<Pair<String, String>>()
    private val isStarted = AtomicBoolean(false)
    private val isConnectingRemote = AtomicBoolean(false)
    private var flushThread: Thread? = null

    @Volatile var isLocalConnected: Boolean = false; private set
    @Volatile var isRemoteConnected: Boolean = false; private set
    @Volatile var messagesForwarded: Long = 0; private set

    fun start() {
        if (isStarted.getAndSet(true)) return

        val settings = SettingsManager.getInstance(context)

        try {
            connectLocal(settings)
            connectRemote(settings)
            startFlushThread()
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
        flushThread?.interrupt()
        flushThread = null
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

        localClient = MqttAsyncClient(localUrl, "blex-bridge-local", MemoryPersistence())
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

        // If client exists and is connected or connecting, skip
        if (isRemoteConnected || remoteClient?.isConnected == true || isConnectingRemote.get()) {
            return
        }
        
        isConnectingRemote.set(true)
        log(LogLevel.INFO, "Connecting to remote: $remoteUrl")

        if (remoteClient == null) {
            val clientId = settings.remoteClientId.ifEmpty { "blex-bridge-remote" }
            remoteClient = MqttAsyncClient(remoteUrl, clientId, MemoryPersistence())
        }
        
        remoteClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                isRemoteConnected = true
                isConnectingRemote.set(false)
                log(LogLevel.INFO, "Remote connectComplete (reconnect=$reconnect)")
                flushOfflineQueue()
            }
            override fun connectionLost(cause: Throwable?) {
                isRemoteConnected = false
                isConnectingRemote.set(false)
                log(LogLevel.WARN, "Remote connection lost: ${cause?.message}")
            }
            override fun messageArrived(topic: String, message: MqttMessage) {}
            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        val opts = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = settings.mqttConnectionTimeout
            keepAliveInterval = settings.mqttKeepAlive
            if (settings.remoteUsername.isNotEmpty()) {
                userName = settings.remoteUsername
                password = settings.remotePassword.toCharArray()
            }
            mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
            maxInflight = 100 // Handle high frequency data better
            
            // TLS for ssl:// or wss:// connections
            if (settings.remoteTlsEnabled) {
                try {
                    socketFactory = getSocketFactory(settings)
                    
                    // IF we have a custom CA cert OR we are NOT strict, disable hostname verification.
                    // NOTE: We rely on HostnameInsensitiveSocketFactory patching the SSL parameters.
                    // We DO NOT set sslHostnameVerifier here because it can cause some libraries 
                    // to re-enable endpoint identification.
                    if (settings.remoteCaCertUri.isNotEmpty() || !settings.remoteTlsStrict) {
                        log(LogLevel.DEBUG, "Hostname verification bypass enabled via SocketFactory")
                    }
                } catch (e: Exception) {
                    log(LogLevel.ERROR, "Failed to setup TLS factory: ${e.message}")
                }
            }
        }

        try {
            remoteClient?.connect(opts, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    isRemoteConnected = true
                    isConnectingRemote.set(false)
                    log(LogLevel.INFO, "Remote connection successful")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isRemoteConnected = false
                    isConnectingRemote.set(false)
                    val errorDetail = when(exception) {
                        is MqttException -> "MqttException (${exception.reasonCode}): ${exception.message} -> ${exception.cause?.message ?: "no cause"}"
                        else -> exception?.message ?: "unknown error"
                    }
                    log(LogLevel.ERROR, "Remote connect failed: $errorDetail")
                }
            })
        } catch (e: Exception) {
            isConnectingRemote.set(false)
            log(LogLevel.ERROR, "Remote connect immediate error: ${e.message}")
        }
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
        val settings = SettingsManager.getInstance(context)
        val interval = settings.upstreamPublishIntervalS

        if (interval > 0) {
            // Buffer it, the flush thread will handle it
            queueMessage(topic, payload)
        } else {
            // Direct forwarding
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
    }

    private fun queueMessage(topic: String, payload: String) {
        if (offlineQueue.size >= MAX_QUEUE_SIZE) {
            offlineQueue.poll() // Drop oldest
        }
        offlineQueue.offer(topic to payload)
    }

    private fun flushOfflineQueue() {
        var flushed = 0
        while (offlineQueue.isNotEmpty() && isRemoteConnected && remoteClient?.isConnected == true) {
            val (topic, payload) = offlineQueue.poll() ?: break
            try {
                val msg = MqttMessage(payload.toByteArray()).apply { qos = 1 }
                remoteClient?.publish(topic, msg)
                messagesForwarded++
                flushed++
                // Add a small delay to prevent saturating the outbound pipe
                Thread.sleep(20) 
            } catch (e: Exception) {
                log(LogLevel.WARN, "Flush failed: ${e.message}")
                break
            }
        }
        if (flushed > 0) {
            log(LogLevel.INFO, "Flushed $flushed messages to remote server")
        }
    }

    private fun startFlushThread() {
        if (flushThread != null) return
        flushThread = Thread {
            log(LogLevel.DEBUG, "Flush thread started")
            while (isStarted.get()) {
                try {
                    val settings = SettingsManager.getInstance(context)
                    val interval = settings.upstreamPublishIntervalS
                    
                    // Always try to flush if there's data, but wait if interval is set
                    if (interval > 0) {
                        Thread.sleep(interval * 1000L)
                    } else {
                        // High speed mode: check every 100ms
                        Thread.sleep(100)
                    }
                    
                    if (isRemoteConnected && remoteClient?.isConnected == true && offlineQueue.isNotEmpty()) {
                        flushOfflineQueue()
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Flush thread error", e)
                }
            }
            log(LogLevel.DEBUG, "Flush thread stopped")
        }.apply {
            name = "MqttBridge-Flush"
            isDaemon = true
            start()
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

    private fun getSocketFactory(settings: SettingsManager): SSLSocketFactory {
        val caCertUri = settings.remoteCaCertUri
        
        // Use custom CA if provided
        if (caCertUri.isNotEmpty()) {
            return try {
                val cf = java.security.cert.CertificateFactory.getInstance("X.509")
                val caInput: java.io.InputStream = context.contentResolver.openInputStream(android.net.Uri.parse(caCertUri))
                    ?: throw java.io.FileNotFoundException("Could not open certificate stream")

                val ca: java.security.cert.X509Certificate = caInput.use { cf.generateCertificate(it) as java.security.cert.X509Certificate }
                log(LogLevel.DEBUG, "Loaded CA: ${ca.subjectDN}")

                val keyStoreType = java.security.KeyStore.getDefaultType()
                val keyStore = java.security.KeyStore.getInstance(keyStoreType).apply {
                    load(null, null)
                    setCertificateEntry("ca", ca)
                }

                val tmfAlgorithm = javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
                val tmf = javax.net.ssl.TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                    init(keyStore)
                }
                log(LogLevel.DEBUG, "TrustManagers initialized: ${tmf.trustManagers.size}")

                val sslContext = javax.net.ssl.SSLContext.getInstance("TLS").apply {
                    init(null, tmf.trustManagers, java.security.SecureRandom())
                }
                HostnameInsensitiveSocketFactory(sslContext.socketFactory)
            } catch (e: Exception) {
                log(LogLevel.ERROR, "CA Cert Load Error: ${e.message}")
                Log.e(TAG, "Failed to load CA certificate from URI: $caCertUri", e)
                if (settings.remoteTlsStrict) getSecureSystemSocketFactory() else getUnsafeSocketFactory()
            }
        }

        // No custom CA - Check Strict Mode
        return if (settings.remoteTlsStrict) {
            getSecureSystemSocketFactory() // Uses system default (Works for Domains)
        } else {
            getUnsafeSocketFactory() // Bypasses checks (Works for Self-signed IPs)
        }
    }

    private fun getSecureSystemSocketFactory(): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, java.security.SecureRandom())
        return HostnameInsensitiveSocketFactory(sslContext.socketFactory)
    }

    /**
     * Creates a socket factory that trusts self-signed certificates.
     * EXTREMELY USEFUL for raw IP addresses without official SSL certs.
     */
    private fun getUnsafeSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return HostnameInsensitiveSocketFactory(sslContext.socketFactory)
    }

    /**
     * Specialized SSLSocketFactory that disables Endpoint Identification.
     * This is CRITICAL for raw IP addresses which usually fail the 
     * default "HTTPS" hostname verification even if the certificate is trusted.
     */
    private class HostnameInsensitiveSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        private fun patch(socket: java.net.Socket): java.net.Socket {
            if (socket is SSLSocket) {
                try {
                    val params = socket.sslParameters
                    params.endpointIdentificationAlgorithm = ""
                    socket.sslParameters = params
                } catch (e: Exception) {
                    Log.w("BleGod.SSL", "Failed to disable endpoint ID", e)
                }
            }
            return socket
        }

        override fun createSocket(s: java.net.Socket?, host: String?, port: Int, autoClose: Boolean): java.net.Socket = patch(delegate.createSocket(s, host, port, autoClose))
        override fun createSocket(): java.net.Socket = patch(delegate.createSocket())
        override fun createSocket(host: String?, port: Int): java.net.Socket = patch(delegate.createSocket(host, port))
        override fun createSocket(host: String?, port: Int, localHost: java.net.InetAddress?, localPort: Int): java.net.Socket = patch(delegate.createSocket(host, port, localHost, localPort))
        override fun createSocket(host: java.net.InetAddress?, port: Int): java.net.Socket = patch(delegate.createSocket(host, port))
        override fun createSocket(address: java.net.InetAddress?, port: Int, localAddress: java.net.InetAddress?, localPort: Int): java.net.Socket = patch(delegate.createSocket(address, port, localAddress, localPort))
    }
}
