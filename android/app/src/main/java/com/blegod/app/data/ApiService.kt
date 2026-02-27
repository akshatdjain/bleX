package com.blegod.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP client for AsseTrack asset_api CRUD operations.
 * All methods are suspend functions — call from a coroutine scope.
 */
object ApiService {

    /** Set this once on screen launch via SettingsManager.getInstance(context).apiBaseUrl */
    var configuredBaseUrl: String = ""

    private fun baseUrl(): String = configuredBaseUrl.trimEnd('/')

    private fun isConfigured(): Boolean = baseUrl().isNotBlank()

    // ─── Generic HTTP helpers ─────────────────────────────────

    private suspend fun httpGet(path: String): String = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl()}$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.inputStream.bufferedReader().use { it.readText() }
    }

    private suspend fun httpPost(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl()}$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.outputStream.write(body.toString().toByteArray())
        conn.outputStream.flush()
        val resp = conn.inputStream.bufferedReader().use { it.readText() }
        JSONObject(resp)
    }

    private suspend fun httpPut(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl()}$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.outputStream.write(body.toString().toByteArray())
        conn.outputStream.flush()
        val resp = conn.inputStream.bufferedReader().use { it.readText() }
        JSONObject(resp)
    }

    private suspend fun httpDelete(path: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl()}$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val resp = conn.inputStream.bufferedReader().use { it.readText() }
        JSONObject(resp)
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

    suspend fun deleteScanner(scannerId: Int): JSONObject {
        return httpDelete("/api/scanners/$scannerId")
    }
}
