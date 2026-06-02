package com.blex.app.ui.screens.configurator

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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

    // Reusable push function — now includes MQTT broker IP and port
    fun pushWifiToScanner(ip: String, ssid: String, psk: String, onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://$ip:8888/provision")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val isCloudMode = provisionMode == "cloud"
                val mqttHost = if (isCloudMode) AppConfig.REMOTE_MQTT_HOST else getDeviceIpAddress()
                val mqttPort = if (isCloudMode) AppConfig.REMOTE_MQTT_PORT_TLS
                               else if (settings.brokerEnabled) settings.brokerPort else settings.mqttPort
                val body = org.json.JSONObject().apply {
                    // Only include WiFi creds if provided — Pi skips WiFi setup if absent
                    if (ssid.isNotBlank()) {
                        put("ssid", ssid)
                        put("psk", psk)
                    }
                    put("mqtt_host", mqttHost)
                    put("mqtt_port", mqttPort)
                    put("use_tls", isCloudMode)
                    put("tenant_id", settings.tenantId)
                    put("mode", provisionMode)
                }.toString()
                conn.outputStream.write(body.toByteArray())
                conn.outputStream.flush()
                val code = conn.responseCode
                withContext(Dispatchers.Main) {
                    if (code == 200) onResult(true, ip)
                    else onResult(false, "$ip: HTTP $code")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "$ip: ${e.message}")
                }
            }
        }
    }

    fun pushMqttToScanner(ip: String, onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://$ip:8888/provision")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val isCloudMode = settings.remoteUseWebSocket
                val mqttHost = if (isCloudMode) AppConfig.REMOTE_MQTT_HOST else getDeviceIpAddress()
                val mqttPort = if (isCloudMode) AppConfig.REMOTE_MQTT_PORT_TLS
                               else if (settings.brokerEnabled) settings.brokerPort else settings.mqttPort
                val body = org.json.JSONObject().apply {
                    put("mqtt_host", mqttHost)
                    put("mqtt_port", mqttPort)
                    put("use_tls", isCloudMode)
                    put("tenant_id", settings.tenantId)
                    put("mode", if (isCloudMode) "cloud" else "local")
                }.toString()
                conn.outputStream.write(body.toByteArray())
                conn.outputStream.flush()
                val code = conn.responseCode
                withContext(Dispatchers.Main) {
                    if (code == 200) onResult(true, ip)
                    else onResult(false, "$ip: HTTP $code")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "$ip: ${e.message}")
                }
            }
        }
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
                    // Role selector
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
                            pushWifiToScanner(s.ip, "", "") { _, _ -> }
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
                            settings.remoteUsername = "tab"
                            settings.remotePassword = "1234"
                            context.sendBroadcast(Intent("com.blex.app.ACTION_RESTART_SERVICE"))
                            // Auto-push cloud mode to all discovered Pis (no WiFi creds — mode change only)
                            for (s in scanners) {
                                pushWifiToScanner(s.ip, "", "") { _, _ -> }
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
                    val ssid = settings.siteWifiSsid
                    val psk = settings.siteWifiPsk
                    pushingMac = scanner.mac
                    scannerPushState = scannerPushState + (scanner.mac to "Provisioning as ${selectedProvisionRole}...")
                    pushWifiToScanner(scanner.ip, ssid, psk) { success, msg ->
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
                                        pushWifiToScanner(s.ip, ssid, psk) { success, _ ->
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
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
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
                    onProvision = {
                        provisionRoleTarget = scanner
                        selectedProvisionRole = settings.getScannerRole(scanner.mac)
                        showProvisionRoleDialog = true
                    },
                    onRegister = {
                        registerScannerTarget = scanner
                        registerScannerName = scanner.name
                        registerScannerResult = null
                        showRegisterScannerDialog = true
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
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

@Composable
fun ScannerCard(
    scanner: DiscoveredScanner,
    isRegistered: Boolean,
    isPushing: Boolean = false,
    pushResult: String? = null,
    savedSsid: String = "",
    onProvision: () -> Unit,
    onRegister: () -> Unit
) {
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

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                    Text(scanner.ip, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
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
                scanner.mac,
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
                // Registered: only Re-Provision
                OutlinedButton(
                    onClick = onProvision,
                    modifier = Modifier.fillMaxWidth(),
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
