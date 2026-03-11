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
import java.security.cert.X509Certificate
import javax.net.ssl.*
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
        private const val TAG = "BleX.MQTT"
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
            val notification = NotificationCompat.Builder(context, BleXApp.ALERT_CHANNEL_ID)
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
            val clientId = "blex_${AppConfig.getDeviceId(context)}"

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
                        try {
                            socketFactory = getSocketFactory(settings)
                            
                            // IF we have a custom CA cert OR we are NOT strict, disable hostname verification.
                            // NOTE: We rely on HostnameInsensitiveSocketFactory patching the SSL parameters.
                            if (settings.remoteCaCertUri.isNotEmpty() || !settings.remoteTlsStrict) {
                                log(LogLevel.DEBUG, "Hostname verification bypass enabled via SocketFactory")
                            }
                        } catch (e: Exception) {
                            log(LogLevel.ERROR, "Failed to setup TLS factory: ${e.message}")
                            Log.e(TAG, "Failed to setup TLS factory", e)
                        }
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
                    isConnected = true
                    isConnecting.set(false)
                    log(LogLevel.INFO, "Direct MQTT connection successful")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnected = false
                    isConnecting.set(false)
                    val errorDetail = when(exception) {
                        is MqttException -> "MqttException (${exception.reasonCode}): ${exception.message} -> ${exception.cause?.message ?: "no cause"}"
                        else -> exception?.message ?: "unknown error"
                    }
                    log(LogLevel.ERROR, "Direct MQTT connect failed: $errorDetail")
                }
            })

        } catch (e: Exception) {
            isConnecting.set(false)
            log(LogLevel.ERROR, "Failed to create/connect MQTT client: ${e.message}")
        }
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
