package com.blex.app.data

import com.blex.app.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP client for asset_api CRUD operations.
 * All methods are suspend functions — call from a coroutine scope.
 * X-Tenant-ID header is injected automatically on every request.
 */
object ApiService {

    /** Set at login from SettingsManager. Falls back to AppConfig.REMOTE_API_URL. */
    var configuredBaseUrl: String = ""

    /** Set at login from SettingsManager.tenantId. Injected as X-Tenant-ID on every request. */
    var tenantId: String = ""

    /** Set at login from SettingsManager.authToken. Injected as Authorization: Bearer on every request. */
    var authToken: String = ""

    // Persisted refresh cookie — getter/setter injected at app startup so the
    // cookie survives process death, app updates, and restarts.
    private var _getRefreshCookie: () -> String = { "" }
    private var _setRefreshCookie: (String) -> Unit = { }
    private val refreshMutex = Mutex()

    /** Call once at app startup with SettingsManager accessors. */
    fun configureRefreshCookie(getter: () -> String, setter: (String) -> Unit) {
        _getRefreshCookie = getter
        _setRefreshCookie = setter
    }

    private fun captureRefreshCookie(conn: HttpURLConnection) {
        conn.headerFields["Set-Cookie"]?.forEach { cookie ->
            if (cookie.startsWith("refresh=")) {
                val value = cookie.split(";").first()
                _setRefreshCookie(value)
            }
        }
    }

    /** Try to get a fresh access token using the stored refresh cookie.
     *  Returns true and updates authToken on success. Thread-safe via mutex. */
    suspend fun tryRefreshToken(): Boolean = refreshMutex.withLock {
        val cookie = _getRefreshCookie()
        if (cookie.isBlank()) return@withLock false
        withContext(Dispatchers.IO) {
            try {
                val conn = URL("${AppConfig.REMOTE_API_URL}/api/auth/refresh")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Cookie", cookie)
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode in 200..299) {
                    val resp = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    authToken = resp.getString("access_token")
                    captureRefreshCookie(conn) // capture rotated cookie if present
                    true
                } else false
            } catch (_: Exception) { false }
        }
    }

    /** Executes [block]. On 401, silently refreshes the token and retries once. */
    private suspend fun <T> withRefresh(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            val isAuthError = e.message?.let {
                it.contains("401") || it.contains("Invalid token", ignoreCase = true) ||
                it.contains("Unauthorized", ignoreCase = true) || it.contains("Missing Bearer", ignoreCase = true)
            } == true
            if (isAuthError && tryRefreshToken()) block() else throw e
        }
    }

    private fun baseUrl(): String =
        configuredBaseUrl.trimEnd('/').ifBlank { AppConfig.REMOTE_API_URL.trimEnd('/') }

    private fun isConfigured(): Boolean = baseUrl().isNotBlank()

    // ─── Generic HTTP helpers ─────────────────────────────────

    private fun HttpURLConnection.applyCommon() {
        connectTimeout = 15000
        readTimeout = 15000
        if (tenantId.isNotBlank()) setRequestProperty("X-Tenant-ID", tenantId)
        if (authToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $authToken")
    }

    private fun HttpURLConnection.readResponse(): String {
        val code = responseCode
        return if (code in 200..299) {
            inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val detail = runCatching { JSONObject(err).optString("detail", "") }.getOrDefault("")
            throw Exception(detail.ifBlank { "HTTP $code" })
        }
    }

    private suspend fun httpGet(path: String): String = withRefresh {
        withContext(Dispatchers.IO) {
            val conn = URL("${baseUrl()}$path").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.applyCommon()
            conn.readResponse()
        }
    }

    private suspend fun httpPost(path: String, body: JSONObject): JSONObject = withRefresh {
        withContext(Dispatchers.IO) {
            val conn = URL("${baseUrl()}$path").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.applyCommon()
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            JSONObject(conn.readResponse())
        }
    }

    private suspend fun httpPut(path: String, body: JSONObject): JSONObject = withRefresh {
        withContext(Dispatchers.IO) {
            val conn = URL("${baseUrl()}$path").openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.applyCommon()
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            JSONObject(conn.readResponse())
        }
    }

    private suspend fun httpDelete(path: String): JSONObject = withRefresh {
        withContext(Dispatchers.IO) {
            val conn = URL("${baseUrl()}$path").openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.applyCommon()
            JSONObject(conn.readResponse())
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ZONES
    // ═══════════════════════════════════════════════════════════

    data class ApiZone(
        val id: Int,
        val zoneName: String,
        val description: String?,
        val scanners: List<ApiScanner>
    )

    suspend fun getZones(): List<ApiZone> {
        if (!isConfigured()) return emptyList()
        val arr = JSONArray(httpGet("/api/zones"))
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val scannersArr = obj.optJSONArray("scanners") ?: JSONArray()
            ApiZone(
                id = obj.optString("id").toIntOrNull() ?: obj.optInt("id", 0),
                zoneName = obj.optString("name").ifBlank { obj.optString("zone_name") },
                description = obj.optString("description", null),
                scanners = (0 until scannersArr.length()).map { j ->
                    val s = scannersArr.getJSONObject(j)
                    ApiScanner(
                        id = s.getInt("id"),
                        macId = s.getString("mac"),
                        name = s.optString("name", null),
                        type = s.optString("type", null)
                    )
                }
            )
        }
    }

    suspend fun createZone(name: String, description: String? = null): JSONObject {
        return httpPost("/api/zones", JSONObject().apply {
            put("zone_name", name)
            if (description != null) put("description", description)
        })
    }

    suspend fun deleteZone(zoneId: Int): JSONObject {
        return httpDelete("/api/zones/$zoneId")
    }

    suspend fun updateZone(zoneId: Int, name: String, description: String? = null): JSONObject {
        return httpPut("/api/zones/$zoneId", JSONObject().apply {
            put("zone_name", name)
            if (description != null) put("description", description)
        })
    }

    suspend fun assignScannerToZone(zoneId: Int, scannerId: Int): JSONObject {
        return httpPost("/api/zones/$zoneId/scanners", JSONObject().apply {
            put("scanner_id", scannerId)
        })
    }

    suspend fun unassignScannerFromZone(zoneId: Int, scannerId: Int): JSONObject {
        return httpDelete("/api/zones/$zoneId/scanners/$scannerId")
    }

    // ═══════════════════════════════════════════════════════════
    // ASSETS (BEACONS)
    // ═══════════════════════════════════════════════════════════

    data class ApiAsset(
        val id: Int,
        val bluetoothId: String,
        val assetName: String?,
        val currentZoneId: Int?
    )

    suspend fun getAssets(): List<ApiAsset> {
        if (!isConfigured()) return emptyList()
        val arr = JSONArray(httpGet("/api/assets"))
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ApiAsset(
                id = obj.getInt("id"),
                bluetoothId = obj.getString("bluetooth_id"),
                assetName = obj.optString("asset_name", null),
                currentZoneId = if (obj.isNull("current_zone_id")) null else obj.getInt("current_zone_id")
            )
        }
    }

    suspend fun registerAsset(mac: String, name: String?): JSONObject {
        return httpPost("/api/assets", JSONObject().apply {
            put("bluetooth_id", mac)
            if (name != null) put("asset_name", name)
        })
    }

    suspend fun updateAsset(assetId: Int, mac: String, name: String?): JSONObject {
        return httpPut("/api/assets/$assetId", JSONObject().apply {
            put("bluetooth_id", mac)
            if (name != null) put("asset_name", name)
        })
    }

    suspend fun deleteAsset(assetId: Int): JSONObject {
        return httpDelete("/api/assets/$assetId")
    }

    // ═══════════════════════════════════════════════════════════
    // SCANNERS
    // ═══════════════════════════════════════════════════════════

    data class ApiScanner(
        val id: Int,
        val macId: String,
        val name: String?,
        val type: String?
    )

    suspend fun getScanners(): List<ApiScanner> {
        if (!isConfigured()) return emptyList()
        val arr = JSONArray(httpGet("/api/scanners"))
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ApiScanner(
                id = obj.getInt("id"),
                macId = obj.getString("mac_id"),
                name = obj.optString("name", null),
                type = obj.optString("type", null)
            )
        }
    }

    suspend fun registerScanner(mac: String, name: String?, type: String?): JSONObject {
        return httpPost("/api/scanners", JSONObject().apply {
            put("mac_id", mac)
            if (name != null) put("name", name)
            if (type != null) put("type", type)
        })
    }

    /** Upsert: register or update a scanner by MAC (overwrite existing entry). */
    suspend fun upsertScanner(mac: String, name: String?, type: String?): JSONObject {
        return httpPut("/api/scanners/by-mac/${mac.uppercase()}", JSONObject().apply {
            put("mac_id", mac.uppercase())
            if (name != null) put("name", name)
            if (type != null) put("type", type)
        })
    }

    suspend fun deleteScanner(scannerId: Int): JSONObject {
        return httpDelete("/api/scanners/$scannerId")
    }

    // ═══════════════════════════════════════════════════════════
    // MASTER PI DISCOVERY
    // ═══════════════════════════════════════════════════════════

    data class MasterInfo(
        val masterIp: String,
        val tenantId: String
    )

    /** Register a Pi as master in mst_master — called in addition to registerScanner when role=master. */
    suspend fun registerMaster(mac: String, ip: String, tenantId: String): JSONObject =
        withContext(Dispatchers.IO) {
            httpPost("/api/runtime/master", JSONObject().apply {
                put("role", "master")
                put("mac", mac.uppercase())
                put("ip", ip)
                put("tenant_id", tenantId)
                put("timestamp", java.time.Instant.now().toString())
            })
        }

    /** Called after login — gets the tenant's registered master Pi IP from DGX.
     *  Returns null if no master is registered yet (cloud-only setup is fine). */
    suspend fun getMasterIp(): MasterInfo? = withContext(Dispatchers.IO) {
        try {
            val resp = httpGet("/api/runtime/master")
            val json = JSONObject(resp)
            if (json.optBoolean("ok", false)) {
                MasterInfo(
                    masterIp = json.optString("master_ip", ""),
                    tenantId = json.optString("tenant_id", "")
                )
            } else null
        } catch (e: Exception) {
            null  // 404 = no master registered yet, that's OK for cloud mode
        }
    }

    // ═══════════════════════════════════════════════════════════
    // TENANT CONFIG (server-side source of truth for provisioning)
    // ═══════════════════════════════════════════════════════════

    data class TabletFallback(val host: String, val port: Int)

    data class TenantConfig(
        val tenantId: String,
        val mode: String,                 // "local" or "cloud"
        val mqttHost: String,
        val mqttPort: Int,
        val useTls: Boolean,
        val mqttUsername: String?,
        val mqttPassword: String?,
        val tabletFallback: TabletFallback?
    )

    /** Get full provisioning config for a tenant.
     *  Used by ScannersTab when provisioning a Pi — DGX is source of truth for
     *  mode/credentials, app no longer asks the user. */
    suspend fun getTenantConfig(tenantId: String): TenantConfig? = withContext(Dispatchers.IO) {
        try {
            val resp = httpGet("/api/tenants/$tenantId/config")
            val j = JSONObject(resp)
            val tabletObj = j.optJSONObject("tablet_fallback")
            val tablet = if (tabletObj != null) TabletFallback(
                host = tabletObj.optString("host", ""),
                port = tabletObj.optInt("port", 1883),
            ) else null
            TenantConfig(
                tenantId     = j.optString("tenant_id", tenantId),
                mode         = j.optString("mode", "cloud"),
                mqttHost     = j.optString("mqtt_host", ""),
                mqttPort     = j.optInt("mqtt_port", 8883),
                useTls       = j.optBoolean("use_tls", true),
                mqttUsername = j.optString("mqtt_username", "").ifBlank { null },
                mqttPassword = j.optString("mqtt_password", "").ifBlank { null },
                tabletFallback = tablet,
            )
        } catch (e: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PI PROVISIONING
    // ═══════════════════════════════════════════════════════════

    data class DeviceToken(val apiToken: String, val deviceId: String)

    /**
     * Step 1 of Pi provisioning: ask DGX to issue a fresh API token for this Pi's MAC.
     * The server stores sha256(token) in shared.devices; returns the plaintext once.
     * Tenant is derived from the caller's JWT — no need to pass tenant_id.
     */
    suspend fun issueDeviceToken(mac: String, role: String = "master", tenantId: String = ""): DeviceToken? =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("mac", mac)
                    put("role", role)
                }
                val resp = httpPost("/api/devices/provision", body)
                DeviceToken(
                    apiToken  = resp.getString("api_token"),
                    deviceId  = resp.getString("device_id"),
                )
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Step 2 of Pi provisioning: push full config + issued API token to the Pi's
     * local provisioner service (port 8888). Returns true on success.
     * piHost is the Pi's local IP discovered via UDP broadcast.
     */
    suspend fun provisionPi(
        piHost: String,
        tenantConfig: TenantConfig,
        apiToken: String,
        piMac: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("tenant_id",    tenantConfig.tenantId)
                put("mode",         tenantConfig.mode)
                put("mqtt_host",    tenantConfig.mqttHost)
                put("mqtt_port",    tenantConfig.mqttPort)
                put("use_tls",      tenantConfig.useTls)
                put("mqtt_username",tenantConfig.mqttUsername ?: "")
                put("mqtt_password",tenantConfig.mqttPassword ?: "")
                put("api_token",    apiToken)
                tenantConfig.tabletFallback?.let { tb ->
                    put("tablet_fallback", JSONObject().apply {
                        put("host", tb.host)
                        put("port", tb.port)
                    })
                }
            }
            val url = java.net.URL("http://$piHost:8888/provision")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout    = 15_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════
    // AUTHENTICATION
    // ═══════════════════════════════════════════════════════════

    data class AuthResponse(
        val accessToken: String,
        val tenantId: String,
        val userId: Long,
        val name: String,
        val email: String,
        val role: String,
        val orgName: String,
        val mqttPrefix: String
    )

    /** Parses the unified auth response shape: {access_token, token_type, user: {...}}.
     *  Top-level org_name/mqtt_prefix are no longer guaranteed; fall back to nested. */
    private fun parseAuthResponse(json: JSONObject): AuthResponse {
        val userObj = json.optJSONObject("user")
        return if (userObj != null) {
            AuthResponse(
                accessToken = json.getString("access_token"),
                tenantId    = userObj.optString("tenant_id", json.optString("tenant_id", "")),
                userId      = userObj.optLong("id", json.optLong("user_id", 0L)),
                name        = userObj.optString("name", json.optString("name", "")),
                email       = userObj.optString("email", json.optString("email", "")),
                role        = userObj.optString("role", "user"),
                orgName     = userObj.optString("org_name", json.optString("org_name", "")),
                mqttPrefix  = userObj.optString("mqtt_prefix", json.optString("mqtt_prefix", "")),
            )
        } else {
            // Legacy flat shape
            AuthResponse(
                accessToken = json.getString("access_token"),
                tenantId    = json.optString("tenant_id", ""),
                userId      = json.optLong("user_id", 0L),
                name        = json.optString("name", ""),
                email       = json.optString("email", ""),
                role        = json.optString("role", "user"),
                orgName     = json.optString("org_name", ""),
                mqttPrefix  = json.optString("mqtt_prefix", ""),
            )
        }
    }

    suspend fun register(name: String, email: String, password: String, orgName: String): AuthResponse =
        withContext(Dispatchers.IO) {
            val url = URL("${AppConfig.REMOTE_API_URL}/api/auth/register")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 20000
            val body = JSONObject().apply {
                put("name", name)
                put("email", email)
                put("password", password)
                put("org_name", orgName)
            }
            conn.outputStream.write(body.toString().toByteArray())
            conn.outputStream.flush()
            val code = conn.responseCode
            val resp = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                throw Exception(JSONObject(err).optString("detail", "Registration failed"))
            }
            val parsed = parseAuthResponse(JSONObject(resp))
            // Wire ApiService for subsequent calls
            authToken = parsed.accessToken
            if (parsed.tenantId.isNotBlank()) tenantId = parsed.tenantId
            parsed
        }

    suspend fun login(email: String, password: String): AuthResponse =
        withContext(Dispatchers.IO) {
            val url = URL("${AppConfig.REMOTE_API_URL}/api/auth/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 20000
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            conn.outputStream.write(body.toString().toByteArray())
            conn.outputStream.flush()
            val code = conn.responseCode
            val resp = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                throw Exception(JSONObject(err).optString("detail", "Login failed"))
            }
            val parsed = parseAuthResponse(JSONObject(resp))
            authToken = parsed.accessToken
            if (parsed.tenantId.isNotBlank()) tenantId = parsed.tenantId
            captureRefreshCookie(conn)
            parsed
        }

}
