package com.blegod.app.network

import android.content.Context
import android.util.Log
import com.blegod.app.data.DiscoveredScanner
import com.blegod.app.data.ScanRepository
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

/**
 * UdpDiscoveryManager — Listens on UDP port 9000 for heartbeat broadcasts
 * sent by Pi and ESP32 scanners running the provisioning scripts.
 *
 * Each heartbeat looks like:
 * { "mac": "B8:27:EB:xx:xx:xx", "ip": "192.168.x.x", "type": "pi"|"esp32", "uptime": 123 }
 *
 * Discovered devices are pushed directly into ScanRepository so the
 * ConfiguratorScreen Scanners tab updates in real time.
 */
class UdpDiscoveryManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null
    private var staleJob: Job? = null

    companion object {
        private const val TAG = "UdpDiscovery"
        const val UDP_PORT = 9000
        private const val BUFFER_SIZE = 1024
        private const val STALE_TIMEOUT_SEC = 10L  // remove after 10s silence
    }

    fun start() {
        if (listenJob?.isActive == true) return
        Log.i(TAG, "Starting UDP discovery on port $UDP_PORT")

        listenJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(UDP_PORT)
                socket.soTimeout = 2000
                val buf = ByteArray(BUFFER_SIZE)

                while (isActive) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length).trim()
                        parseHeartbeat(msg, packet.address.hostAddress ?: "")
                    } catch (_: SocketTimeoutException) {
                        // Normal — just re-poll
                    } catch (e: Exception) {
                        if (isActive) Log.w(TAG, "Receive error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open socket: ${e.message}")
            } finally {
                socket?.close()
            }
        }

        // Stale device pruner — runs every 5s
        staleJob = scope.launch {
            while (isActive) {
                delay(5000)
                pruneStale()
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        staleJob?.cancel()
        Log.i(TAG, "UDP discovery stopped")
    }

    private fun parseHeartbeat(json: String, fromIp: String) {
        try {
            val obj = JSONObject(json)
            val mac = obj.optString("mac", "").ifBlank { return }
            val ip = obj.optString("ip", fromIp).ifBlank { fromIp }
            val type = obj.optString("type", "unknown")
            val hostname = obj.optString("hostname", "")
            val displayName = when (type) {
                "pi" -> if (hostname.isNotBlank()) hostname else "Pi-${mac.takeLast(5)}"
                "esp32" -> "ESP32-${mac.takeLast(5)}"
                else -> mac
            }

            ScanRepository.addOrUpdateScanner(
                DiscoveredScanner(
                    name = displayName,
                    mac = mac,
                    ip = ip,
                    type = type,
                    lastSeenMs = System.currentTimeMillis(),
                    status = "Active"
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Bad heartbeat payload: $json — ${e.message}")
        }
    }

    private fun pruneStale() {
        val now = System.currentTimeMillis()
        val current = ScanRepository.discoveredScanners.value
        val stale = current.filter { now - it.lastSeenMs > STALE_TIMEOUT_SEC * 1000 }
        if (stale.isNotEmpty()) {
            Log.i(TAG, "Pruning ${stale.size} stale scanner(s)")
            ScanRepository.removeStaleScanners(stale.map { it.mac }.toSet())
        }
    }
}
