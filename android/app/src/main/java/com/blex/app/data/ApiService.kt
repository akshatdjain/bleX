package com.blex.app.data

import com.blex.app.AppConfig
import kotlinx.coroutines.Dispatchers
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

    private fun baseUrl(): String =
        configuredBaseUrl.trimEnd('/').ifBlank { AppConfig.REMOTE_API_URL.trimEnd('/') }

    private fun isConfigured(): Boolean = baseUrl().isNotBlank()

    // ─── Generic HTTP helpers ─────────────────────────────────

    private fun HttpURLConnection.applyCommon() {
        connectTimeout = 15000
        readTimeout = 15000
        if (tenantId.isNotBlank()) setRequestProperty("X-Tenant-ID", tenantId)
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

    private suspend fun httpGet(path: String): String = withContext(Dispatchers.IO) {
        val conn = URL("${baseUrl()}$path").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.applyCommon()
        conn.readResponse()
    }

    private suspend fun httpPost(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL("${baseUrl()}$path").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.applyCommon()
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        JSONObject(conn.readResponse())
    }

    private suspend fun httpPut(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL("${baseUrl()}$path").openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.applyCommon()
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        JSONObject(conn.readResponse())
    }

    private suspend fun httpDelete(path: String): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL("${baseUrl()}$path").openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.applyCommon()
        JSONObject(conn.readResponse())
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
                id = obj.getInt("id"),
                zoneName = obj.getString("zone_name"),
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
    // AUTHENTICATION
    // ═══════════════════════════════════════════════════════════

    data class AuthResponse(
        val accessToken: String,
        val tenantId: String,
        val userId: Long,
        val name: String,
        val email: String,
        val orgName: String,
        val mqttPrefix: String
    )

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
            val json = JSONObject(resp)
            AuthResponse(
                accessToken = json.getString("access_token"),
                tenantId = json.getString("tenant_id"),
                userId = json.getLong("user_id"),
                name = json.getString("name"),
                email = json.getString("email"),
                orgName = json.getString("org_name"),
                mqttPrefix = json.getString("mqtt_prefix")
            )
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
            val json = JSONObject(resp)
            AuthResponse(
                accessToken = json.getString("access_token"),
                tenantId = json.getString("tenant_id"),
                userId = json.getLong("user_id"),
                name = json.getString("name"),
                email = json.getString("email"),
                orgName = json.getString("org_name"),
                mqttPrefix = json.getString("mqtt_prefix")
            )
        }
}
