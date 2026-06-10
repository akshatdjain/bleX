package com.blex.app.ui.screens.configurator

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items
import com.blex.app.AppConfig
import com.blex.app.BuildConfig
import com.blex.app.data.ApiService
import com.blex.app.data.DiscoveredScanner
import com.blex.app.data.ScanRepository
import com.blex.app.data.SettingsManager
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope

@Composable
fun ScannersTab() {
    val scanners by ScanRepository.discoveredScanners.collectAsState()
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }

    // Read actual Wi-Fi or Cellular MAC address from NetworkInterface.
    // Android 6+ returns 02:00:00:00:00:00 from WifiInfo, so we read from /sys interface.
    fun getNetworkMac(): Pair<String, String> {
        try {
            // Try Wi-Fi first (wlan0)
            val wlanIface = java.net.NetworkInterface.getByName("wlan0")
            if (wlanIface != null) {
                val hwAddr = wlanIface.hardwareAddress
                if (hwAddr != null && hwAddr.isNotEmpty()) {
                    val mac = hwAddr.joinToString(":") { String.format("%02X", it) }
                    if (mac != "02:00:00:00:00:00") return Pair(mac, "Wi-Fi")
                }
            }
            // Try cellular (rmnet0, rmnet_data0, etc.)
            val cellInterfaces = listOf("rmnet0", "rmnet_data0", "ccmni0", "seth_w0")
            for (ifName in cellInterfaces) {
                val iface = java.net.NetworkInterface.getByName(ifName)
                if (iface != null) {
                    val hwAddr = iface.hardwareAddress
                    if (hwAddr != null && hwAddr.isNotEmpty()) {
                        val mac = hwAddr.joinToString(":") { String.format("%02X", it) }
                        if (mac != "02:00:00:00:00:00") return Pair(mac, "Cellular")
                    }
                }
            }
            // Fallback: enumerate all interfaces
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || iface.name.startsWith("lo")) continue
                val hwAddr = iface.hardwareAddress ?: continue
                if (hwAddr.isEmpty()) continue
                val mac = hwAddr.joinToString(":") { String.format("%02X", it) }
                if (mac != "02:00:00:00:00:00") return Pair(mac, iface.name)
            }
        } catch (_: Exception) {}
        // Ultimate fallback: use ANDROID_ID formatted as MAC
        val aid = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "0000000000000000"
        return Pair(aid.chunked(2).take(6).joinToString(":").uppercase(), "AndroidID")
    }

    val networkMacResult = remember { getNetworkMac() }
    var tabletMac by remember { mutableStateOf(networkMacResult.first) }
    var networkType by remember { mutableStateOf(networkMacResult.second) }
    var showReRegisterBanner by remember { mutableStateOf(false) }
    var previousMac by remember { mutableStateOf(networkMacResult.first) }

    val isRealMac = remember(tabletMac) { tabletMac.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")) }
    val tabletModel = remember { android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() } + " " + android.os.Build.MODEL }

    // Global WiFi Settings State
    var showWifiSettings by remember { mutableStateOf(false) }
    var savedWifiInfoMsg by remember { mutableStateOf<String?>(null) }
    var siteWifiSsid by remember { mutableStateOf(settings.siteWifiSsid) }
    var siteWifiPsk by remember { mutableStateOf(settings.siteWifiPsk) }
    // "local" = Pi→Tablet MQTT, "cloud" = Pi→DGX direct
    var provisionMode by remember { mutableStateOf(settings.scannerProvisionMode) }

    // Per-scanner push state: Map<Mac, String?>
    var scannerPushState by remember { mutableStateOf(mapOf<String, String?>()) }
    var pushingMac by remember { mutableStateOf<String?>(null) }

    // Provision role dialog
    var showProvisionRoleDialog by remember { mutableStateOf(false) }
    var provisionRoleTarget by remember { mutableStateOf<DiscoveredScanner?>(null) }
    var selectedProvisionRole by remember { mutableStateOf("scanner") }

    // Mode switch confirmation dialogs
    var showCloudConfirmDialog by remember { mutableStateOf(false) }
    var showLocalConfirmDialog by remember { mutableStateOf(false) }

    // Push-to-All state
    var isPushingAll by remember { mutableStateOf(false) }
    var pushAllResultMsg by remember { mutableStateOf<String?>(null) }

    // Shared registration dialog (used for both tablet and discovered scanners)
    var showRegisterScannerDialog by remember { mutableStateOf(false) }
    var registerScannerTarget by remember { mutableStateOf<DiscoveredScanner?>(null) }
    // Tablet-specific registration state
    var showRegisterTabletDialog by remember { mutableStateOf(false) }
    var registerTabletName by remember { mutableStateOf(tabletModel) }
    var registerTabletResult by remember { mutableStateOf<String?>(null) }
    var isRegisteringTablet by remember { mutableStateOf(false) }

    var registerScannerName by remember { mutableStateOf("") }
    var registerScannerResult by remember { mutableStateOf<String?>(null) }
    var isRegisteringScanner by remember { mutableStateOf(false) }

    // Registered scanners from API (to show status)
    var dbScanners by remember { mutableStateOf<List<ApiService.ApiScanner>>(emptyList()) }
    val registeredMacs = remember(dbScanners) { dbScanners.map { it.macId.uppercase() }.toSet() }

    // Rename scanner dialog
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DiscoveredScanner?>(null) }
    var renameText by remember { mutableStateOf("") }
    var isRenaming by remember { mutableStateOf(false) }
    var renameResult by remember { mutableStateOf<String?>(null) }

    // Monitor network changes — re-read MAC every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val (newMac, newType) = getNetworkMac()
            if (newMac != tabletMac) {
                previousMac = tabletMac
                tabletMac = newMac
                networkType = newType
                // Only show re-register banner if already registered with old MAC
                if (registeredMacs.contains(previousMac.uppercase())) {
                    showReRegisterBanner = true
                }
            }
        }
    }

    val scope = rememberCoroutineScope()

    // Auto-detect this device's IP for MQTT broker info
    fun getDeviceIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val ip = wifiManager.connectionInfo.ipAddress
            if (ip != 0) {
                return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
            }
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "192.168.43.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.43.1"
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingScanners by remember { mutableStateOf(true) }

    fun refreshScanners() {
        scope.launch {
            if (!isRefreshing) isLoadingScanners = true
            isRefreshing = true
            val startTime = System.currentTimeMillis()
            ApiService.configuredBaseUrl = settings.apiBaseUrl
            dbScanners = try { ApiService.getScanners() } catch (_: Exception) { emptyList() }
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 220) kotlinx.coroutines.delay(220 - elapsed)
            isLoadingScanners = false
            isRefreshing = false
        }
    }

    // Load registered scanners on mount
    LaunchedEffect(Unit) { refreshScanners() }

    /** Build the provisioning JSON for a Pi.
     *  Source of truth = DGX tenant config (mode, broker, creds).
     *  Tablet fallback host falls back to this device's own LAN IP if the
     *  tenant has no tablet configured server-side. */
    suspend fun buildProvisionBody(mac: String, ssid: String, psk: String, role: String): org.json.JSONObject {
        val cfg = ApiService.getTenantConfig(settings.tenantId)
        val tabletHost = cfg?.tabletFallback?.host?.takeIf { it.isNotBlank() } ?: getDeviceIpAddress()
        val tabletPort = cfg?.tabletFallback?.port ?: (if (settings.brokerEnabled) settings.brokerPort else 1883)
        val mode  = provisionMode  // app switch is source of truth; Option B will sync to server

        // Determine MQTT creds: server config → stored settings → BuildConfig fallback
        val host  = cfg?.mqttHost?.takeIf { it.isNotBlank() } ?: AppConfig.REMOTE_MQTT_HOST
        val port  = cfg?.mqttPort ?: AppConfig.REMOTE_MQTT_PORT_TLS
        val tls   = cfg?.useTls ?: true
        val user  = cfg?.mqttUsername?.takeIf { it.isNotBlank() }
            ?: settings.remoteUsername.takeIf { it.isNotBlank() }
            ?: BuildConfig.MQTT_USERNAME
        val pass  = cfg?.mqttPassword?.takeIf { it.isNotBlank() }
            ?: settings.remotePassword.takeIf { it.isNotBlank() }
            ?: BuildConfig.MQTT_PASSWORD

        // Persist MQTT creds to settings so app bridge connection also uses them
        if (user.isNotBlank() && mode == "cloud") {
            settings.remoteUsername = user
            settings.remotePassword = pass
            settings.remoteHost = host
            settings.remotePort = AppConfig.REMOTE_MQTT_PORT_WSS
            settings.remoteTlsEnabled = true
            settings.remoteUseWebSocket = true
            settings.remoteWebSocketPath = AppConfig.REMOTE_MQTT_WSS_PATH
        }

        // Read stored token. If missing, auto-issue one now (handles pre-registered
        // scanners and fresh APK installs where Register wasn't tapped with new APK).
        val storedToken = settings.getDeviceToken(mac)
        val deviceToken: String = if (!storedToken.isNullOrBlank()) {
            android.util.Log.d("ScannersTab", "Using stored token for $mac: ${storedToken.take(8)}...")
            storedToken
        } else {
            android.util.Log.d("ScannersTab", "No stored token for $mac — auto-issuing from DGX")
            try {
                val issued = ApiService.issueDeviceToken(mac = mac, role = role, tenantId = settings.tenantId)
                if (issued != null) {
                    settings.setDeviceToken(mac, issued.apiToken)
                    android.util.Log.d("ScannersTab", "Auto-issued token for $mac: ${issued.apiToken.take(8)}...")
                    issued.apiToken
                } else {
                    android.util.Log.w("ScannersTab", "Auto token issuance returned null for $mac")
                    ""
                }
            } catch (e: Exception) {
                android.util.Log.e("ScannersTab", "Auto token issuance FAILED for $mac: ${e.message}")
                ""
            }
        }

        return org.json.JSONObject().apply {
            if (ssid.isNotBlank()) { put("ssid", ssid); put("psk", psk) }
            put("tenant_id", settings.tenantId)
            put("role", role)
            put("mode", mode)
            put("mqtt_host", host)
            put("mqtt_port", port)
            put("use_tls", tls)
            put("mqtt_username", user)
            put("mqtt_password", pass)
            put("api_token", deviceToken)
            put("tablet_fallback", org.json.JSONObject().apply {
                put("host", tabletHost)
                put("port", tabletPort)
            })
        }
    }

    fun pushWifiToScanner(ip: String, mac: String, ssid: String, psk: String, role: String = "scanner", onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://$ip:8888/provision")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val body = buildProvisionBody(mac, ssid, psk, role).toString()
                conn.outputStream.write(body.toByteArray())
                conn.outputStream.flush()
                val code = conn.responseCode
                withContext(Dispatchers.Main) {
                    if (code == 200) {
                        // Restart bridge so it picks up any updated remote MQTT creds
                        context.sendBroadcast(android.content.Intent("com.blex.app.ACTION_RESTART_SERVICE"))
                        onResult(true, ip)
                    } else onResult(false, "$ip: HTTP $code")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "$ip: ${e.message}")
                }
            }
        }
    }


    // ── Rename Scanner Dialog ──
    if (showRenameDialog && renameTarget != null) {
        AlertDialog(
            onDismissRequest = { if (!isRenaming) { showRenameDialog = false; renameResult = null } },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text("Rename Scanner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MAC: ${renameTarget!!.mac}", style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline)
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it },
                        label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    renameResult?.let {
                        Text(it, color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = renameTarget ?: return@Button
                    isRenaming = true; renameResult = "Saving..."
                    scope.launch {
                        try {
                            ApiService.configuredBaseUrl = settings.apiBaseUrl
                            ApiService.upsertScanner(target.mac, renameText.trim().ifBlank { target.name }, target.type)
                            dbScanners = try { ApiService.getScanners() } catch (_: Exception) { dbScanners }
                            isRenaming = false; showRenameDialog = false; renameResult = null
                        } catch (e: Exception) { renameResult = "Failed: ${e.message}"; isRenaming = false }
                    }
                }, enabled = !isRenaming && renameText.isNotBlank()) {
                    if (isRenaming) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isRenaming) { showRenameDialog = false; renameResult = null } }) { Text("Cancel") }
            }
        )
    }

    // ── Tablet Registration Dialog ──
    if (showRegisterTabletDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRegisteringTablet) { showRegisterTabletDialog = false; registerTabletResult = null } },
            icon = { Icon(Icons.Default.TabletAndroid, null) },
            title = { Text("Register This Tablet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Device ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(tabletMac, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedTextField(
                        value = registerTabletName,
                        onValueChange = { registerTabletName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    registerTabletResult?.let {
                        Text(it, color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isRegisteringTablet = true
                        registerTabletResult = "Registering..."
                        scope.launch {
                            try {
                                ApiService.configuredBaseUrl = settings.apiBaseUrl
                                ApiService.upsertScanner(tabletMac, registerTabletName.trim().ifBlank { tabletModel }, "android")
                                dbScanners = try { ApiService.getScanners() } catch (_: Exception) { dbScanners }
                                isRegisteringTablet = false
                                showRegisterTabletDialog = false
                            } catch (e: Exception) {
                                registerTabletResult = "Failed: ${e.message}"
                                isRegisteringTablet = false
                            }
                        }
                    },
                    enabled = !isRegisteringTablet
                ) {
                    if (isRegisteringTablet) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Text("Register")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isRegisteringTablet) { showRegisterTabletDialog = false; registerTabletResult = null } }) { Text("Cancel") }
            }
        )
    }

    // ── Network Scanner Registration Dialog ──
    if (showRegisterScannerDialog && registerScannerTarget != null) {
        val registerRole = remember(registerScannerTarget) {
            mutableStateOf(settings.getScannerRole(registerScannerTarget!!.mac))
        }
        AlertDialog(
            onDismissRequest = { if (!isRegisteringScanner) { showRegisterScannerDialog = false; registerScannerResult = null } },
            icon = { Icon(Icons.Default.AppRegistration, null) },
            title = { Text("Register Scanner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MAC: ${registerScannerTarget!!.mac}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
                    // Role selector — Master only available in local mode
                    if (provisionMode == "local") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("scanner" to "Scanner", "master" to "Master").forEach { (role, label) ->
                                FilterChip(
                                    selected = registerRole.value == role,
                                    onClick = { registerRole.value = role },
                                    label = { Text(label) },
                                    leadingIcon = if (registerRole.value == role) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        registerRole.value = "scanner"
                    }
                    if (registerRole.value == "master") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Hub, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    "This BleX node will act as the local tracking hub",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = registerScannerName,
                        onValueChange = { registerScannerName = it },
                        label = { Text("Name (e.g. Gate-A)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    registerScannerResult?.let {
                        Text(it, color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = registerScannerTarget ?: return@Button
                        val role = registerRole.value
                        isRegisteringScanner = true
                        registerScannerResult = "Registering..."
                        scope.launch {
                            try {
                                ApiService.configuredBaseUrl = settings.apiBaseUrl
                                // Always register in mst_scanner
                                ApiService.registerScanner(target.mac, registerScannerName.trim().ifBlank { target.name }, target.type)
                                // Issue a per-device API token from DGX and store it securely.
                                try {
                                    val device = ApiService.issueDeviceToken(
                                        mac = target.mac,
                                        role = role,
                                        tenantId = settings.tenantId,
                                    )
                                    if (device != null) {
                                        settings.setDeviceToken(target.mac, device.apiToken)
                                        android.util.Log.d("ScannersTab", "API token issued for ${target.mac}: ${device.apiToken.take(8)}...")
                                        registerScannerResult = "✓ Registered + API token issued"
                                    } else {
                                        android.util.Log.w("ScannersTab", "issueDeviceToken returned null for ${target.mac}")
                                        registerScannerResult = "⚠ Registered but token issue failed (null)"
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ScannersTab", "issueDeviceToken FAILED for ${target.mac}: ${e.message}")
                                    registerScannerResult = "⚠ Registered but token failed: ${e.message}"
                                }
                                // If master: also register in mst_master so scanner_boot.py can fetch the IP
                                if (role == "master") {
                                    ApiService.registerMaster(target.mac, target.ip, settings.tenantId)
                                    settings.localMasterIp = target.ip
                                    settings.remoteHost = target.ip
                                    settings.remotePort = 1883
                                    settings.remoteTlsEnabled = false
                                    settings.remoteUseWebSocket = false
                                    context.sendBroadcast(Intent("com.blex.app.ACTION_RESTART_SERVICE"))
                                }
                                settings.setScannerRole(target.mac, role)
                                dbScanners = try { ApiService.getScanners() } catch (_: Exception) { dbScanners }
                                isRegisteringScanner = false
                                showRegisterScannerDialog = false
                                registerScannerResult = null
                            } catch (e: Exception) {
                                registerScannerResult = "Failed: ${e.message}"
                                isRegisteringScanner = false
                            }
                        }
                    },
                    enabled = !isRegisteringScanner
                ) {
                    if (isRegisteringScanner) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Text("Register")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isRegisteringScanner) { showRegisterScannerDialog = false; registerScannerResult = null } }) { Text("Cancel") }
            }
        )
    }

    // ── Local Mode Confirmation Dialog ──
    if (showLocalConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLocalConfirmDialog = false },
            icon = { Icon(Icons.Default.Hub, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Switch to Local Mode?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    listOf(
                        "Restore master Pi IP (${settings.localMasterIp.ifEmpty { "will fetch from server" }})",
                        "Switch tablet bridge → master Pi at port 1883",
                        "Pi master service will resume handling zone logic"
                    ).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = MaterialTheme.colorScheme.primary)
                            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showLocalConfirmDialog = false
                    provisionMode = "local"
                    settings.scannerProvisionMode = "local"
                    scope.launch {
                        // Restore saved master IP — no API needed, preserves whatever Pi was master before
                        val savedIp = settings.localMasterIp
                        if (savedIp.isNotEmpty()) {
                            settings.remoteHost = savedIp
                        } else {
                            // Fallback: ask DGX (first time or cleared storage)
                            try {
                                val master = ApiService.getMasterIp()
                                if (master != null && master.masterIp.isNotEmpty()) {
                                    settings.remoteHost = master.masterIp
                                    settings.localMasterIp = master.masterIp
                                }
                            } catch (_: Exception) {}
                        }
                        settings.remoteUseWebSocket = false
                        settings.remoteTlsEnabled = false
                        settings.remotePort = 1883
                        context.sendBroadcast(Intent("com.blex.app.ACTION_RESTART_SERVICE"))
                        // Auto-push local mode to all discovered Pis (no WiFi creds — mode change only)
                        for (s in scanners) {
                            pushWifiToScanner(s.ip, s.mac, "", "") { _, _ -> }
                        }
                    }
                }) {
                    Icon(Icons.Default.Hub, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Switch to Local")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocalConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Cloud Mode Confirmation Dialog ──
    if (showCloudConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCloudConfirmDialog = false },
            icon = { Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.secondary) },
            title = { Text("Switch to Cloud Mode?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This will:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    listOf(
                        "Push MQTT config to all Pis → they publish to DGX directly",
                        "Stop the local master service on any master Pi",
                        "Switch tablet bridge to WSS → DGX"
                    ).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = MaterialTheme.colorScheme.secondary)
                            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloudConfirmDialog = false
                        provisionMode = "cloud"
                        settings.scannerProvisionMode = "cloud"
                        scope.launch {
                            settings.remoteHost = AppConfig.REMOTE_MQTT_HOST
                            settings.remotePort = AppConfig.REMOTE_MQTT_PORT_WSS
                            settings.remoteTlsEnabled = true
                            settings.remoteUseWebSocket = true
                            settings.remoteWebSocketPath = AppConfig.REMOTE_MQTT_WSS_PATH
                            settings.remoteUsername = BuildConfig.MQTT_USERNAME
                            settings.remotePassword = BuildConfig.MQTT_PASSWORD
                            context.sendBroadcast(Intent("com.blex.app.ACTION_RESTART_SERVICE"))
                            // Auto-push cloud mode to all discovered Pis (no WiFi creds — mode change only)
                            for (s in scanners) {
                                pushWifiToScanner(s.ip, s.mac, "", "") { _, _ -> }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Cloud, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Switch to Cloud")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Provision Role Dialog ──
    if (showProvisionRoleDialog && provisionRoleTarget != null) {
        val target = provisionRoleTarget!!
        AlertDialog(
            onDismissRequest = { showProvisionRoleDialog = false },
            icon = { Icon(Icons.Default.WifiTethering, null) },
            title = { Text("Provision ${target.ip}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Choose how this BleX node should work in your network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (provisionMode == "cloud") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                Text("Cloud mode — provisioning as Scanner. Zone logic runs on the cloud master.",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("scanner" to "Scanner", "master" to "Master").forEach { (role, label) ->
                                FilterChip(
                                    selected = selectedProvisionRole == role,
                                    onClick = { selectedProvisionRole = role },
                                    label = { Text(label) },
                                    leadingIcon = if (selectedProvisionRole == role) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    if (selectedProvisionRole == "master" && provisionMode == "local") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Hub, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    "This tablet will connect directly to this BleX node for local tracking",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showProvisionRoleDialog = false
                    val scanner = provisionRoleTarget ?: return@Button
                    pushingMac = scanner.mac
                    scannerPushState = scannerPushState + (scanner.mac to "Provisioning as ${selectedProvisionRole}...")
                    // No WiFi creds — Provision only changes mode/role, not WiFi
                    pushWifiToScanner(scanner.ip, scanner.mac, "", "", selectedProvisionRole) { success, msg ->
                        val result = if (success) "✓ Provisioned as $selectedProvisionRole" else "Failed: $msg"
                        scannerPushState = scannerPushState + (scanner.mac to result)
                        if (pushingMac == scanner.mac) pushingMac = null
                        if (success) {
                            // Remember role for this scanner so Re-Provision pre-selects it
                            settings.setScannerRole(scanner.mac, selectedProvisionRole)
                            // If provisioned as master in local mode, save IP as broker + persist it
                            if (selectedProvisionRole == "master" && provisionMode == "local") {
                                settings.localMasterIp = scanner.ip
                                settings.remoteHost = scanner.ip
                                settings.remotePort = 1883
                                settings.remoteTlsEnabled = false
                                settings.remoteUseWebSocket = false
                                context.sendBroadcast(Intent("com.blex.app.ACTION_RESTART_SERVICE"))
                            }
                        }
                    }
                }) {
                    Icon(Icons.Default.WifiTethering, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Provision")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProvisionRoleDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Always show the full list — tablet card is always at top
    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refreshScanners() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // ── This Tablet Section ──
        item {
            Text(
                "THIS TABLET",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                letterSpacing = 1.5.sp
            )
        }
        item {
            if (isLoadingScanners) {
                SkeletonTabletCard()
            } else {
                val isTabletRegistered = registeredMacs.contains(tabletMac.uppercase())
                ThisTabletCard(
                    modelName = tabletModel,
                    mac = tabletMac,
                    networkType = networkType,
                    isRegistered = isTabletRegistered,
                    onRegister = {
                        registerTabletName = tabletModel
                        registerTabletResult = null
                        showRegisterTabletDialog = true
                    }
                )
            }
        }

        // All remaining items hidden during skeleton — prevents layout shift
        if (isLoadingScanners) {
            item { SkeletonWifiModeCard() }
            return@LazyColumn
        }

        // ── Network Change Re-Register Banner ──
        if (showReRegisterBanner) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Network Changed",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "New MAC detected ($networkType). Please re-register to update your identity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                registerTabletName = tabletModel
                                registerTabletResult = null
                                showRegisterTabletDialog = true
                                showReRegisterBanner = false
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Re-Register")
                        }
                    }
                }
            }
        }

        // ── Network Scanners Section ──
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "NETWORK SCANNERS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.5.sp
                    )
                    if (scanners.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "${scanners.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = { showWifiSettings = !showWifiSettings },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(if (showWifiSettings) Icons.Default.ExpandLess else Icons.Default.Wifi, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WiFi", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = showWifiSettings,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Router, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Scanner Wi-Fi Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Optional — only needed if your Pi isn't already on the site Wi-Fi. Leave blank to only push MQTT config.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        OutlinedTextField(
                            value = siteWifiSsid,
                            onValueChange = { siteWifiSsid = it },
                            label = { Text("Site Wi-Fi SSID (optional)") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Wifi, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = siteWifiPsk,
                            onValueChange = { siteWifiPsk = it },
                            label = { Text("Password (optional)") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            if (savedWifiInfoMsg != null) {
                                Text(savedWifiInfoMsg!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 12.dp))
                            }
                            Button(onClick = {
                                settings.siteWifiSsid = siteWifiSsid
                                settings.siteWifiPsk = siteWifiPsk
                                savedWifiInfoMsg = "✓ Saved"
                                scope.launch { delay(2000); savedWifiInfoMsg = null; showWifiSettings = false }
                            }) {
                                Text("Save Credentials")
                            }
                        }
                        if (scanners.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Button(
                                onClick = {
                                    val ssid = settings.siteWifiSsid
                                    val psk = settings.siteWifiPsk
                                    isPushingAll = true
                                    val label = if (ssid.isBlank()) "MQTT config" else "WiFi + MQTT"
                                    pushAllResultMsg = "Pushing $label to ${scanners.size} scanner(s)..."
                                    var successCount = 0; var failCount = 0; val total = scanners.size
                                    for (s in scanners) {
                                        pushWifiToScanner(s.ip, s.mac, ssid, psk) { success, _ ->
                                            if (success) successCount++ else failCount++
                                            if (successCount + failCount == total) {
                                                isPushingAll = false
                                                pushAllResultMsg = "✓ Done: $successCount sent, $failCount failed"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isPushingAll,
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                if (isPushingAll) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(8.dp)); Text("Pushing...")
                                } else {
                                    Icon(Icons.Default.WifiTethering, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp)); Text("Push WiFi to All (${scanners.size})")
                                }
                            }
                            pushAllResultMsg?.let { msg ->
                                Text(msg, color = if (msg.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── Scanner Mode Card ──────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (provisionMode == "local")
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (provisionMode == "local") Icons.Default.Hub else Icons.Default.Cloud,
                            null,
                            tint = if (provisionMode == "local") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        Text("Scanner Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "local" to "Local  (Pi → Tablet)",
                            "cloud" to "Cloud  (Pi → DGX)"
                        ).forEach { (value, label) ->
                            val selected = provisionMode == value
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (value == "cloud" && provisionMode != "cloud") {
                                        showCloudConfirmDialog = true
                                    } else if (value == "local" && provisionMode != "local") {
                                        showLocalConfirmDialog = true
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    val modeInfo = if (provisionMode == "local")
                        "Local mode: BleX nodes report to the hub, which syncs to the cloud. Best for sites with poor internet."
                    else
                        "Cloud mode: BleX nodes report directly to the cloud. Best for reliable internet connections."
                    Text(modeInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        if (scanners.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Text("Listening for scanners...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Run discovery_broadcast.py / boot.py on your Pi or ESP32",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        items(scanners, key = { it.mac }) { scanner ->
                val isRegistered = registeredMacs.contains(scanner.mac.uppercase())
                ScannerCard(
                    scanner = scanner,
                    isRegistered = isRegistered,
                    isPushing = pushingMac == scanner.mac,
                    pushResult = scannerPushState[scanner.mac],
                    savedSsid = settings.siteWifiSsid,
                    registeredName = dbScanners.find { it.macId.uppercase() == scanner.mac.uppercase() }?.name,
                    onProvision = {
                        provisionRoleTarget = scanner
                        selectedProvisionRole = if (provisionMode == "cloud") "scanner"
                            else settings.getScannerRole(scanner.mac)
                        showProvisionRoleDialog = true
                    },
                    onRegister = {
                        registerScannerTarget = scanner
                        registerScannerName = scanner.name
                        registerScannerResult = null
                        showRegisterScannerDialog = true
                    },
                    onRename = {
                        renameTarget = scanner
                        renameText = dbScanners.find { it.macId.uppercase() == scanner.mac.uppercase() }?.name ?: scanner.name
                        renameResult = null
                        showRenameDialog = true
                    },
                    onUnregister = {
                        val dbScanner = dbScanners.find { it.macId.uppercase() == scanner.mac.uppercase() }
                        if (dbScanner != null) {
                            scope.launch {
                                try {
                                    ApiService.configuredBaseUrl = settings.apiBaseUrl
                                    ApiService.deleteScanner(dbScanner.id)
                                    dbScanners = dbScanners.filter { it.id != dbScanner.id }
                                    settings.setScannerRole(scanner.mac, "scanner")
                                } catch (_: Exception) {}
                            }
                        }
                    }
                )
            }
        }
    }
}

// ─── Tablet Card ────────────────────────────────────────────────────────────

@Composable
fun ThisTabletCard(modelName: String, mac: String, networkType: String = "Wi-Fi", isRegistered: Boolean, onRegister: () -> Unit) {
    val tabletColor = MaterialTheme.colorScheme.tertiary
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!isRegistered)
                MaterialTheme.colorScheme.surface
            else
                tabletColor.copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.background(tabletColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Tablet", color = tabletColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(modelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isRegistered) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text("Registered ✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        networkType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (!isRegistered) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Register this tablet as a scanner to include it in zones and reporting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onRegister,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = tabletColor),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.AppRegistration, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Register This Tablet")
                }
            }
        }
    }
}

// ── Skeleton Wi-Fi + Mode Card ────────────────────────────────────────────────

@Composable
fun SkeletonWifiModeCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "wifi_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wifi_shimmer_alpha"
    )
    val sv = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.alpha(alpha)) {
        // Wi-Fi creds skeleton
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.width(140.dp).height(14.dp), RoundedCornerShape(4.dp), color = sv) {}
                Surface(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(8.dp), color = sv) {}
                Surface(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(8.dp), color = sv) {}
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(Modifier.width(100.dp).height(36.dp), RoundedCornerShape(8.dp), color = sv) {}
                }
            }
        }
        // Mode card skeleton
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.width(120.dp).height(14.dp), RoundedCornerShape(4.dp), color = sv) {}
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.weight(1f).height(36.dp), RoundedCornerShape(8.dp), color = sv) {}
                    Surface(Modifier.weight(1f).height(36.dp), RoundedCornerShape(8.dp), color = sv) {}
                }
                Surface(Modifier.fillMaxWidth(0.8f).height(11.dp), RoundedCornerShape(4.dp), color = sv) {}
            }
        }
    }
}

// ── Skeleton Scanner Card ────────────────────────────────────────────────────

@Composable
fun SkeletonScannerCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scanner_shimmer_alpha"
    )
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    ElevatedCard(modifier = Modifier.fillMaxWidth().alpha(alpha)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row: type badge + IP + status dot
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.width(44.dp).height(22.dp), RoundedCornerShape(8.dp), color = surfaceVariant) {}
                    Surface(Modifier.width(110.dp).height(14.dp), RoundedCornerShape(4.dp), color = surfaceVariant) {}
                }
                Surface(Modifier.size(8.dp), RoundedCornerShape(4.dp), color = surfaceVariant) {}
            }
            // MAC address line
            Surface(Modifier.width(180.dp).height(12.dp), RoundedCornerShape(4.dp), color = surfaceVariant) {}
            // Last seen + result row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(Modifier.width(80.dp).height(11.dp), RoundedCornerShape(4.dp), color = surfaceVariant) {}
                Surface(Modifier.width(60.dp).height(11.dp), RoundedCornerShape(4.dp), color = surfaceVariant) {}
            }
            Spacer(Modifier.height(2.dp))
            // Button row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(Modifier.weight(1f).height(36.dp), RoundedCornerShape(8.dp), color = surfaceVariant) {}
                Surface(Modifier.weight(1.1f).height(36.dp), RoundedCornerShape(8.dp), color = surfaceVariant) {}
            }
        }
    }
}

// ── Skeleton Tablet Card ──────────────────────────────────────────────────────

@Composable
fun SkeletonTabletCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "tablet_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tablet_shimmer_alpha"
    )
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    ElevatedCard(modifier = Modifier.fillMaxWidth().alpha(alpha)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.width(52.dp).height(22.dp), RoundedCornerShape(8.dp), color = surfaceVariant) {}
                    Surface(Modifier.width(130.dp).height(14.dp), RoundedCornerShape(4.dp), color = surfaceVariant) {}
                }
                Surface(Modifier.width(80.dp).height(20.dp), RoundedCornerShape(6.dp), color = surfaceVariant) {}
            }
            Surface(Modifier.width(160.dp).height(12.dp), RoundedCornerShape(4.dp), color = surfaceVariant) {}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScannerCard(
    scanner: DiscoveredScanner,
    isRegistered: Boolean,
    isPushing: Boolean = false,
    pushResult: String? = null,
    savedSsid: String = "",
    registeredName: String? = null,
    onProvision: () -> Unit,
    onRegister: () -> Unit,
    onRename: (() -> Unit)? = null,
    onUnregister: (() -> Unit)? = null
) {
    var showUnregisterConfirm by remember { mutableStateOf(false) }

    if (showUnregisterConfirm) {
        AlertDialog(
            onDismissRequest = { showUnregisterConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Unregister Scanner?") },
            text = {
                Text(
                    "This removes ${scanner.name} (${scanner.mac}) from the database. The Pi itself is unaffected — re-provision to add it back.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showUnregisterConfirm = false; onUnregister?.invoke() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Unregister") }
            },
            dismissButton = {
                TextButton(onClick = { showUnregisterConfirm = false }) { Text("Cancel") }
            }
        )
    }
    val typeColor = when (scanner.type) {
        "pi" -> MaterialTheme.colorScheme.tertiary
        "esp32" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val typeLabel = when (scanner.type) {
        "pi" -> "Pi"
        "esp32" -> "ESP32"
        else -> scanner.type.uppercase()
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { if (isRegistered) showUnregisterConfirm = true }
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(typeLabel, color = typeColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(registeredName?.takeIf { it.isNotBlank() } ?: scanner.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isRegistered) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text("Registered ✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            Text(
                "${scanner.ip} · ${scanner.mac}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                fontFamily = FontFamily.Monospace
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Last seen: ${(System.currentTimeMillis() - scanner.lastSeenMs) / 1000}s ago", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                if (pushResult != null) {
                    Text(pushResult, color = if (pushResult.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (isRegistered) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onProvision,
                        modifier = Modifier.weight(1f),
                        enabled = !isPushing,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        if (isPushing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Re-Provision", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (onRename != null) {
                        FilledTonalButton(
                            onClick = onRename,
                            modifier = Modifier.weight(0.7f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Rename", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                // Not registered: Provision + Register side by side
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onProvision,
                        modifier = Modifier.weight(1f),
                        enabled = !isPushing,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        if (isPushing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.WifiTethering, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Provision", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    FilledTonalButton(
                        onClick = onRegister,
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AppRegistration, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Register", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
