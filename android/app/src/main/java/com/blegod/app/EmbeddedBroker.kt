package com.blegod.app

import android.content.Context
import android.util.Log
import com.blegod.app.data.LogLevel
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import java.util.Properties

/**
 * EmbeddedBroker — Wraps Moquette MQTT broker to run inside the Android app.
 *
 * ESP32s, Raspberry Pis, and the tablet itself connect to this broker.
 * The MqttBridge then forwards messages to the remote upstream server.
 */
class EmbeddedBroker(private val context: Context) {

    companion object {
        private const val TAG = "BleGod.Broker"
    }

    private var server: Server? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) {
            log(LogLevel.WARN, "Broker already running, skipping start")
            return
        }

        try {
            val settings = SettingsManager.getInstance(context)
            val storePath = context.filesDir.resolve("moquette").absolutePath

            // Ensure storage directory exists
            context.filesDir.resolve("moquette").mkdirs()

            val props = Properties().apply {
                setProperty("host", "0.0.0.0")
                setProperty("port", settings.brokerPort.toString())
                setProperty("allow_anonymous", "true")
                setProperty("persistent_store", "$storePath/moquette_store.mapdb")
            }

            // Configure authentication if credentials are set
            val username = settings.brokerUsername
            val password = settings.brokerPassword
            if (username.isNotEmpty() && password.isNotEmpty()) {
                props.setProperty("allow_anonymous", "false")
                // Write password file for Moquette
                val passFile = context.filesDir.resolve("moquette/password_file.conf")
                passFile.writeText("$username:$password\n")
                props.setProperty("password_file", passFile.absolutePath)
            }

            server = Server()
            server?.startServer(MemoryConfig(props))
            isRunning = true

            log(LogLevel.INFO, "Broker started on port ${settings.brokerPort}")
        } catch (e: Exception) {
            isRunning = false
            log(LogLevel.ERROR, "Failed to start broker: ${e.message}")
            Log.e(TAG, "Broker start failed", e)
        }
    }

    fun stop() {
        try {
            server?.stopServer()
            server = null
            isRunning = false
            log(LogLevel.INFO, "Broker stopped")
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Error stopping broker: ${e.message}")
            Log.e(TAG, "Broker stop failed", e)
        }
    }

    private fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
        ScanRepository.addBrokerLog(level, "Broker", message)
    }
}
