package com.blegod.app

import android.os.Build
import android.provider.Settings

/**
 * AppConfig — Central configuration for the entire app.
 *
 * WHY A SEPARATE CONFIG FILE?
 * When you deploy this on multiple tablets, you only need to change values HERE.
 * Everything else in the app reads from this file. This is a pattern used in
 * professional apps (like at Google) to keep configuration separate from logic.
 *
 * WHAT EACH VALUE DOES — explained below.
 */
object AppConfig {

    // ══════════════════════════════════════════════════════════════
    //  MQTT Configuration
    // ══════════════════════════════════════════════════════════════

    /**
     * MQTT Broker URL — where the MQTT broker is running.
     *
     * tcp://127.0.0.1:1883 means:
     *   - tcp://     → use plain TCP (not encrypted)
     *   - 127.0.0.1  → localhost (the same device)
     *   - :1883      → default MQTT port
     *
     * Since Mosquitto runs on Termux ON THE SAME TABLET, we use localhost.
     * If you ever move the broker to a server, change this to its IP.
     */
    const val MQTT_BROKER_URL = "tcp://127.0.0.1:1883"

    /**
     * MQTT Topic prefix — messages are published to:
     *   blegod/beacons/{DEVICE_ID}
     *
     * The Python subscriber uses wildcard blegod/beacons/# to get ALL tablets.
     */
    const val MQTT_TOPIC_PREFIX = "blegod/beacons"

    /**
     * MQTT Quality of Service (QoS):
     *   0 = "Fire and forget" — fastest, might lose messages
     *   1 = "At least once"   — guaranteed delivery, might get duplicates
     *   2 = "Exactly once"    — slowest, guaranteed no duplicates
     *
     * We use QoS 1 — good balance of speed and reliability for BLE data.
     */
    const val MQTT_QOS = 1

    /**
     * Keep-alive interval in seconds.
     * The client sends a "ping" to the broker every N seconds to prove it's alive.
     * If the broker doesn't hear from us in 1.5x this time, it disconnects us.
     * 30 seconds is a good default.
     */
    const val MQTT_KEEP_ALIVE_SECONDS = 30

    /**
     * Connection timeout in seconds.
     * How long to wait when trying to connect to the broker before giving up.
     */
    const val MQTT_CONNECTION_TIMEOUT = 10

    // ══════════════════════════════════════════════════════════════
    //  BLE Scanning Configuration
    // ══════════════════════════════════════════════════════════════

    /**
     * How often to perform a scan cycle, in milliseconds.
     * 2500ms = 2.5 seconds — scans, collects results, publishes, waits, repeats.
     */
    const val SCAN_INTERVAL_MS = 2500L

    /**
     * How long each individual scan "window" lasts, in milliseconds.
     * During this time, the BLE radio actively listens for advertisements.
     * 2000ms gives us a good chance to catch most nearby beacons.
     */
    const val SCAN_DURATION_MS = 2000L

    /**
     * Force-restart the BLE scanner after this many minutes.
     * Android has an undocumented ~30-minute limit on continuous BLE scans.
     * We restart every 25 minutes to stay well under that limit.
     */
    const val SCAN_RESTART_INTERVAL_MINUTES = 25L

    // ══════════════════════════════════════════════════════════════
    //  Watchdog Configuration
    // ══════════════════════════════════════════════════════════════

    /**
     * How often the watchdog checks if the BLE scanner is healthy (in ms).
     * If no scan results have been received in this time, it restarts the scanner.
     */
    const val WATCHDOG_INTERVAL_MS = 60_000L  // 60 seconds

    /**
     * If no scan results received in this many milliseconds,
     * the watchdog considers the scanner "stalled" and restarts it.
     */
    const val WATCHDOG_STALL_THRESHOLD_MS = 30_000L  // 30 seconds

    // ══════════════════════════════════════════════════════════════
    //  Notification
    // ══════════════════════════════════════════════════════════════

    /**
     * Notification ID — must be > 0, used by the foreground service.
     * This is just an arbitrary number. If you had multiple foreground services,
     * each would need a different ID.
     */
    const val NOTIFICATION_ID = 1001

    // ══════════════════════════════════════════════════════════════
    //  Device Identification
    // ══════════════════════════════════════════════════════════════

    /**
     * Get a unique device ID for this tablet.
     *
     * ANDROID_ID is a 64-bit hex string unique to each device + user combo.
     * It persists across app installs but changes on factory reset.
     * We take the first 8 characters for readability.
     *
     * When deploying on multiple tablets, each will automatically have
     * a different device ID, so the Python code can tell them apart.
     */
    fun getDeviceId(context: android.content.Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        // Take first 8 chars for a shorter, readable ID
        return androidId.take(8)
    }

    /**
     * Build the full MQTT topic for this device.
     * Example: "blegod/beacons/a1b2c3d4"
     */
    fun getMqttTopic(context: android.content.Context): String {
        return "$MQTT_TOPIC_PREFIX/${getDeviceId(context)}"
    }
}
