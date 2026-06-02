package com.blex.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * SettingsManager — Persistent storage for MQTT, scan, theme, and payload configuration.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("blex_settings", Context.MODE_PRIVATE)

    fun getPrefs(): SharedPreferences = prefs

    // ── MQTT Settings ────────────────────────────────────────────

    var mqttHost: String
        get() = prefs.getString("mqtt_host", "127.0.0.1") ?: "127.0.0.1"
        set(value) = prefs.edit().putString("mqtt_host", value).apply()

    var mqttPort: Int
        get() = prefs.getInt("mqtt_port", 1883)
        set(value) = prefs.edit().putInt("mqtt_port", value).apply()

    var mqttTopicPrefix: String
        get() = prefs.getString("mqtt_topic_prefix", "blex/beacons") ?: "blex/beacons"
        set(value) = prefs.edit().putString("mqtt_topic_prefix", value).apply()

    var mqttQos: Int
        get() = prefs.getInt("mqtt_qos", 1)
        set(value) = prefs.edit().putInt("mqtt_qos", value).apply()

    var mqttTlsEnabled: Boolean
        get() = prefs.getBoolean("mqtt_tls_enabled", false)
        set(value) = prefs.edit().putBoolean("mqtt_tls_enabled", value).apply()

    var mqttUsername: String
        get() = prefs.getString("mqtt_username", "") ?: ""
        set(value) = prefs.edit().putString("mqtt_username", value).apply()

    var mqttPassword: String
        get() = prefs.getString("mqtt_password", "") ?: ""
        set(value) = prefs.edit().putString("mqtt_password", value).apply()

    var mqttKeepAlive: Int
        get() = prefs.getInt("mqtt_keep_alive", 30)
        set(value) = prefs.edit().putInt("mqtt_keep_alive", value).apply()

    var mqttConnectionTimeout: Int
        get() = prefs.getInt("mqtt_connection_timeout", 10)
        set(value) = prefs.edit().putInt("mqtt_connection_timeout", value).apply()

    // ── BLE Scan Settings ────────────────────────────────────────

    var scanIntervalMs: Long
        get() = prefs.getLong("scan_interval_ms", 2000L)
        set(value) = prefs.edit().putLong("scan_interval_ms", value).apply()

    var scanDurationMs: Long
        get() = prefs.getLong("scan_duration_ms", 2000L)
        set(value) = prefs.edit().putLong("scan_duration_ms", value).apply()

    /** Scan power mode: LOW_POWER, BALANCED, LOW_LATENCY */
    var scanPowerMode: String
        get() = prefs.getString("scan_power_mode", "LOW_LATENCY") ?: "LOW_LATENCY"
        set(value) = prefs.edit().putString("scan_power_mode", value).apply()

    // ── Theme Settings ───────────────────────────────────────────

    val themeModeFlow = MutableStateFlow(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")

    /** Theme mode: SYSTEM, DARK, LIGHT */
    var themeMode: String
        get() = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        set(value) {
            prefs.edit().putString("theme_mode", value).apply()
            themeModeFlow.value = value
        }

    // ── Scanner Identity ─────────────────────────────────────────

    /** User-settable scanner MAC label (for multi-tablet deployment) */
    var scannerMacLabel: String
        get() = prefs.getString("scanner_mac_label", "") ?: ""
        set(value) = prefs.edit().putString("scanner_mac_label", value).apply()

    // ── MQTT Payload Template ────────────────────────────────────

    /** JSON template for MQTT payload. Uses placeholder tokens. */
    var mqttPayloadTemplate: String
        get() = prefs.getString("mqtt_payload_template", DEFAULT_PAYLOAD_TEMPLATE)
            ?: DEFAULT_PAYLOAD_TEMPLATE
        set(value) = prefs.edit().putString("mqtt_payload_template", value).apply()

    // ── Payload field toggles ────────────────────────────────────

    var payloadIncludeUuid: Boolean
        get() = prefs.getBoolean("payload_include_uuid", true)
        set(value) = prefs.edit().putBoolean("payload_include_uuid", value).apply()

    var payloadIncludeMajorMinor: Boolean
        get() = prefs.getBoolean("payload_include_major_minor", true)
        set(value) = prefs.edit().putBoolean("payload_include_major_minor", value).apply()

    var payloadIncludeTxPower: Boolean
        get() = prefs.getBoolean("payload_include_tx_power", true)
        set(value) = prefs.edit().putBoolean("payload_include_tx_power", value).apply()

    var payloadIncludeBeaconType: Boolean
        get() = prefs.getBoolean("payload_include_beacon_type", true)
        set(value) = prefs.edit().putBoolean("payload_include_beacon_type", value).apply()

    var payloadIncludeName: Boolean
        get() = prefs.getBoolean("payload_include_name", false)
        set(value) = prefs.edit().putBoolean("payload_include_name", value).apply()

    // ── Embedded Broker Settings ─────────────────────────────────

    var brokerEnabled: Boolean
        get() = prefs.getBoolean("broker_enabled", true)
        set(value) = prefs.edit().putBoolean("broker_enabled", value).apply()

    var brokerPort: Int
        get() = prefs.getInt("broker_port", 1883)
        set(value) = prefs.edit().putInt("broker_port", value).apply()

    var brokerUsername: String
        get() = prefs.getString("broker_username", "") ?: ""
        set(value) = prefs.edit().putString("broker_username", value).apply()

    var brokerPassword: String
        get() = prefs.getString("broker_password", "") ?: ""
        set(value) = prefs.edit().putString("broker_password", value).apply()

    // ── Remote Server (Upstream) Settings ────────────────────────

    var remoteHost: String
        get() = prefs.getString("remote_host", "") ?: ""
        set(value) = prefs.edit().putString("remote_host", value).apply()

    var remotePort: Int
        get() = prefs.getInt("remote_port", 443)
        set(value) = prefs.edit().putInt("remote_port", value).apply()

    var remoteTlsEnabled: Boolean
        get() = prefs.getBoolean("remote_tls_enabled", true)
        set(value) = prefs.edit().putBoolean("remote_tls_enabled", value).apply()

    var remoteUseWebSocket: Boolean
        get() = prefs.getBoolean("remote_use_websocket", true)
        set(value) = prefs.edit().putBoolean("remote_use_websocket", value).apply()

    var remoteWebSocketPath: String
        get() = prefs.getString("remote_ws_path", "/mqtt") ?: "/mqtt"
        set(value) = prefs.edit().putString("remote_ws_path", value).apply()

    var remoteUsername: String
        get() = prefs.getString("remote_username", "") ?: ""
        set(value) = prefs.edit().putString("remote_username", value).apply()

    var remotePassword: String
        get() = prefs.getString("remote_password", "") ?: ""
        set(value) = prefs.edit().putString("remote_password", value).apply()

    var remoteClientId: String
        get() = prefs.getString("remote_client_id", "blex-bridge-remote") ?: "blex-bridge-remote"
        set(value) = prefs.edit().putString("remote_client_id", value).apply()

    /** Topic filter for bridge: what local topics to forward upstream */
    var bridgeTopicFilter: String
        get() = prefs.getString("bridge_topic_filter", "#") ?: "#"
        set(value) = prefs.edit().putString("bridge_topic_filter", value).apply()

    /** If true, enforce strict SSL verification (fails on self-signed IPs without a CA cert) */
    var remoteTlsStrict: Boolean
        get() = prefs.getBoolean("remote_tls_strict", false)
        set(value) = prefs.edit().putBoolean("remote_tls_strict", value).apply()

    /** URI of a custom CA certificate file for remote server validation */
    var remoteCaCertUri: String
        get() = prefs.getString("remote_ca_cert_uri", "") ?: ""
        set(value) = prefs.edit().putString("remote_ca_cert_uri", value).apply()

    /** Upstream publish interval in seconds (0 = instant) */
    var upstreamPublishIntervalS: Int
        get() = prefs.getInt("upstream_publish_interval_s", 0)
        set(value) = prefs.edit().putInt("upstream_publish_interval_s", value).apply()

    // ── Site WiFi Credentials (for provisioning) ─────────────────

    var siteWifiSsid: String
        get() = prefs.getString("site_wifi_ssid", "") ?: ""
        set(value) = prefs.edit().putString("site_wifi_ssid", value).apply()

    var siteWifiPsk: String
        get() = prefs.getString("site_wifi_psk", "") ?: ""
        set(value) = prefs.edit().putString("site_wifi_psk", value).apply()

    // "local" or "cloud" — what mode to send when provisioning scanners
    var scannerProvisionMode: String
        get() = prefs.getString("scanner_provision_mode", "cloud") ?: "cloud"
        set(value) = prefs.edit().putString("scanner_provision_mode", value).apply()

    // Last known master Pi IP — persists across cloud/local switches so we can restore it
    var localMasterIp: String
        get() = prefs.getString("local_master_ip", "") ?: ""
        set(value) = prefs.edit().putString("local_master_ip", value).apply()

    // Per-scanner last provisioned role (keyed by MAC) — "scanner" or "master"
    fun getScannerRole(mac: String): String =
        prefs.getString("scanner_role_${mac.uppercase()}", "scanner") ?: "scanner"

    fun setScannerRole(mac: String, role: String) =
        prefs.edit().putString("scanner_role_${mac.uppercase()}", role).apply()

    // ── UI Visibility Settings ────────────────────────────────────

    val logsVisibleFlow = MutableStateFlow(prefs.getBoolean("logs_visible", true))

    /** Whether the Logs item is visible in the navigation drawer */
    var logsVisible: Boolean
        get() = prefs.getBoolean("logs_visible", true)
        set(value) {
            prefs.edit().putBoolean("logs_visible", value).apply()
            logsVisibleFlow.value = value
        }

    // ── API Configuration ────────────────────────────────────────

    var apiBaseUrl: String
        get() = prefs.getString("api_base_url", "") ?: ""
        set(value) = prefs.edit().putString("api_base_url", value).apply()

    var webDashboardUrl: String
        get() = prefs.getString("web_dashboard_url", "https://sigmatic-asc.tech/blex") ?: "https://sigmatic-asc.tech/blex"
        set(value) = prefs.edit().putString("web_dashboard_url", value).apply()

    // ── Auth & Tenant ────────────────────────────────────────────

    var tenantId: String
        get() = prefs.getString("tenant_id", "") ?: ""
        set(value) = prefs.edit().putString("tenant_id", value).apply()

    var authToken: String
        get() = prefs.getString("auth_token", "") ?: ""
        set(value) = prefs.edit().putString("auth_token", value).apply()

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(value) = prefs.edit().putString("user_name", value).apply()

    var userEmail: String
        get() = prefs.getString("user_email", "") ?: ""
        set(value) = prefs.edit().putString("user_email", value).apply()

    var orgName: String
        get() = prefs.getString("org_name", "") ?: ""
        set(value) = prefs.edit().putString("org_name", value).apply()

    val mqttTopicTenant: String
        get() {
            val tid = tenantId
            return if (tid.isNotEmpty()) "ble/$tid/scanner" else "ble/scanner"
        }

    fun isLoggedIn(): Boolean = authToken.isNotEmpty() && tenantId.isNotEmpty()

    fun clearAuth() {
        prefs.edit()
            .remove("auth_token")
            .remove("tenant_id")
            .remove("user_name")
            .remove("user_email")
            .remove("org_name")
            .apply()
    }

    // ── Zone Persistence ─────────────────────────────────────────

    fun saveZones(zones: List<Zone>) {
        val arr = org.json.JSONArray()
        for (zone in zones) {
            val obj = org.json.JSONObject()
            obj.put("id", zone.id)
            obj.put("name", zone.name)
            val scanners = org.json.JSONArray()
            zone.assignedScanners.forEach { scanners.put(it) }
            obj.put("assignedScanners", scanners)
            arr.put(obj)
        }
        prefs.edit().putString("zones_json", arr.toString()).apply()
    }

    fun loadZones(): List<Zone> {
        val json = prefs.getString("zones_json", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val scanners = obj.optJSONArray("assignedScanners")
                val scannerList = if (scanners != null) {
                    (0 until scanners.length()).map { scanners.getString(it) }
                } else emptyList()
                Zone(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    assignedScanners = scannerList
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Derived ──────────────────────────────────────────────────

    /** URL for the local MQTT client to connect to.
     *  Routes through local broker when enabled, else direct to remote. */
    fun getBrokerUrl(): String {
        if (brokerEnabled) {
            return "tcp://127.0.0.1:$brokerPort"
        }
        val protocol = if (mqttTlsEnabled) "ssl" else "tcp"
        return "$protocol://$mqttHost:$mqttPort"
    }

    companion object {
        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }

        val DEFAULT_PAYLOAD_TEMPLATE = """{
  "scanner_mac": "${'$'}{SCANNER_MAC}",
  "beacon_mac": "${'$'}{BEACON_MAC}",
  "rssi": ${'$'}{RSSI},
  "tx_power": ${'$'}{TX_POWER},
  "timestamp_utc": "${'$'}{TIMESTAMP_UTC}"
}"""
    }
}
