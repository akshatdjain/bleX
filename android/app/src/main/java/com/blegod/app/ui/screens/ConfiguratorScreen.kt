package com.blegod.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blegod.app.BeaconData
import com.blegod.app.data.ApiService
import com.blegod.app.data.DiscoveredScanner
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager
import com.blegod.app.data.Zone
import kotlinx.coroutines.*

private const val TAG = "ConfiguratorScreen"

// ─── Screen Root ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguratorScreen(initialTab: Int = 0) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabTitles = listOf("Hotspot", "Scanners", "Zones", "Assets")
    val tabIcons = listOf(
        Icons.Default.Wifi,
        Icons.Default.Router,
        Icons.Default.Map,
        Icons.Default.Label
    )

    // Hidden API config dialog
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    var showApiConfig by remember { mutableStateOf(false) }
    var apiUrl by remember { mutableStateOf(settings.apiBaseUrl) }

    if (showApiConfig) {
        AlertDialog(
            onDismissRequest = { showApiConfig = false },
            icon = { Icon(Icons.Default.Settings, null) },
            title = { Text("API Configuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Asset API base URL (e.g. http://192.168.1.100:8000)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = { apiUrl = it },
                        label = { Text("API Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    settings.apiBaseUrl = apiUrl
                    showApiConfig = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showApiConfig = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    icon = { Icon(tabIcons[index], null, modifier = Modifier.size(18.dp)) },
                    text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState)
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    else
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                },
                label = "TabContent"
            ) { tab ->
                when (tab) {
                    0 -> HotspotTab()
                    1 -> ScannersTab()
                    2 -> ZonesTab()
                    3 -> AssetsTab()
                }
            }
        }
    }
}

// ─── Tab 1: Hotspot ─────────────────────────────────────────────────────────

@Composable
fun HotspotTab() {
    val context = LocalContext.current
    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    // Poll hotspot status every 1.5s
    // getWifiApState() returns: 10=DISABLED, 11=DISABLING, 12=ENABLING, 13=ENABLED
    var isHotspotActive by remember { mutableStateOf(false) }
    var expandedCredentials by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isHotspotActive = try {
                val method = wifiManager.javaClass.getMethod("getWifiApState")
                val state = method.invoke(wifiManager) as Int
                state == 13  // WIFI_AP_STATE_ENABLED
            } catch (e: Exception) {
                // Fallback: try isWifiApEnabled if getWifiApState fails
                try {
                    val m2 = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
                    m2.isAccessible = true
                    m2.invoke(wifiManager) as Boolean
                } catch (_: Exception) {
                    false
                }
            }
            delay(1500)
        }
    }

    // Open tethering/hotspot settings directly
    fun openHotspotSettings() {
        val intents = listOf(
            // Most direct — goes straight to Hotspot page on most Android OEMs
            Intent("android.settings.TETHER_SETTINGS"),
            // Fallback for some devices (Pixel)
            Intent("android.intent.action.MAIN").apply {
                addCategory("android.intent.category.DEFAULT")
                putExtra(":settings:fragment_args_key", "wifi_tether_settings_fragment")
            },
            // Last resort — WiFi settings
            Intent(Settings.ACTION_WIFI_SETTINGS)
        )
        // Try each intent in order — launch first one that resolves
        for (intent in intents) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Hotspot Status Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isHotspotActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isHotspotActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.WifiTethering,
                                null,
                                tint = if (isHotspotActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text("Hotspot Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            Text(
                                if (isHotspotActive) "Active ✓" else "Inactive",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isHotspotActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    // Pulsing dot
                    if (isHotspotActive) {
                        Box(
                            modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        item {
            // Required Credentials Info
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { expandedCredentials = !expandedCredentials },
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Setup Credentials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(if (expandedCredentials) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.outline)
                    }
                    if (expandedCredentials) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("SSID", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text("setup", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Password", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text("setup@1234", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Port (Provision)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text("8888", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Port (Discovery)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text("UDP 9000", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        item {
            // Action Buttons
            if (!isHotspotActive) {
                Button(
                    onClick = { openHotspotSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.WifiOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Hotspot is OFF — Tap to enable")
                }
            } else {
                FilledTonalButton(
                    onClick = { openHotspotSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage Hotspot Settings")
                }
            }
        }

        item {
            if (!isHotspotActive) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Text(
                            "Enable your tablet hotspot first. Scanners (Pi/ESP32) must connect to it before they can be discovered and provisioned.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

// ─── Tab 2: Scanners ────────────────────────────────────────────────────────

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

    // Per-scanner push state: Map<Mac, String?>
    var scannerPushState by remember { mutableStateOf(mapOf<String, String?>()) }
    var pushingMac by remember { mutableStateOf<String?>(null) }

    // Push-to-All state
    var isPushingAll by remember { mutableStateOf(false) }
    var pushAllResultMsg by remember { mutableStateOf<String?>(null) }
    var isPushingMqttAll by remember { mutableStateOf(false) }
    var pushMqttAllResultMsg by remember { mutableStateOf<String?>(null) }

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
            kotlinx.coroutines.delay(5000)
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

    fun refreshScanners() {
        scope.launch {
            isRefreshing = true
            ApiService.configuredBaseUrl = settings.apiBaseUrl
            dbScanners = try { ApiService.getScanners() } catch (_: Exception) { emptyList() }
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
                val mqttIp = getDeviceIpAddress()
                val mqttPort = if (settings.brokerEnabled) settings.brokerPort else settings.mqttPort
                val body = org.json.JSONObject().apply {
                    put("ssid", ssid)
                    put("psk", psk)
                    put("mqtt_host", mqttIp)
                    put("mqtt_port", mqttPort)
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
                val mqttIp = getDeviceIpAddress()
                val mqttPort = if (settings.brokerEnabled) settings.brokerPort else settings.mqttPort
                val body = org.json.JSONObject().apply {
                    put("mqtt_host", mqttIp)
                    put("mqtt_port", mqttPort)
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
                                registerTabletResult = "✓ Registered!"
                                dbScanners = try { ApiService.getScanners() } catch (_: Exception) { dbScanners }
                                isRegisteringTablet = false
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
        AlertDialog(
            onDismissRequest = { if (!isRegisteringScanner) { showRegisterScannerDialog = false; registerScannerResult = null } },
            icon = { Icon(Icons.Default.AppRegistration, null) },
            title = { Text("Register Scanner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MAC: ${registerScannerTarget!!.mac}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
                    Text("Type: ${registerScannerTarget!!.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    OutlinedTextField(
                        value = registerScannerName,
                        onValueChange = { registerScannerName = it },
                        label = { Text("Scanner Name (e.g. Gate-A Scanner)") },
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
                        isRegisteringScanner = true
                        registerScannerResult = "Registering..."
                        scope.launch {
                            try {
                                ApiService.configuredBaseUrl = settings.apiBaseUrl
                                ApiService.registerScanner(target.mac, registerScannerName.trim().ifBlank { target.name }, target.type)
                                registerScannerResult = "✓ Scanner registered!"
                                dbScanners = try { ApiService.getScanners() } catch (_: Exception) { dbScanners }
                                isRegisteringScanner = false
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
                letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
        }
        item {
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
                        letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
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
                    Text("Wi-Fi Creds", style = MaterialTheme.typography.labelSmall)
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
                            "Provide the Wi-Fi credentials scanners should connect to. This will be saved and used when you push to individual scanners.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        OutlinedTextField(
                            value = siteWifiSsid,
                            onValueChange = { siteWifiSsid = it },
                            label = { Text("Site Wi-Fi SSID") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Wifi, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = siteWifiPsk,
                            onValueChange = { siteWifiPsk = it },
                            label = { Text("Password") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Dns, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Tablet IP (MQTT Host)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(getDeviceIpAddress(), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
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
                    }
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Push to all button
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val ssid = settings.siteWifiSsid
                                val psk = settings.siteWifiPsk
                                if (ssid.isBlank() || psk.isBlank()) {
                                    pushAllResultMsg = "Set WiFi credentials first — push to any single scanner to save them"
                                    return@Button
                                }
                                isPushingAll = true
                                pushAllResultMsg = "Pushing to ${scanners.size} scanner(s)..."
                                var successCount = 0; var failCount = 0; val total = scanners.size
                                for (scanner in scanners) {
                                    pushWifiToScanner(scanner.ip, ssid, psk) { success, _ ->
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
                        
                        // Push MQTT to All
                        Button(
                            onClick = {
                                isPushingMqttAll = true
                                pushMqttAllResultMsg = "Pushing MQTT to ${scanners.size} scanner(s)..."
                                var successCount = 0; var failCount = 0; val total = scanners.size
                                for (scanner in scanners) {
                                    pushMqttToScanner(scanner.ip) { success, _ ->
                                        if (success) successCount++ else failCount++
                                        if (successCount + failCount == total) {
                                            isPushingMqttAll = false
                                            pushMqttAllResultMsg = "✓ MQTT Done: $successCount sent, $failCount failed"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPushingMqttAll,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            if (isPushingMqttAll) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSecondary)
                                Spacer(Modifier.width(8.dp)); Text("Pushing MQTT...")
                            } else {
                                Icon(Icons.Default.SettingsInputAntenna, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp)); Text("Push MQTT to All (${scanners.size})")
                            }
                        }
                        
                        val savedSsid = settings.siteWifiSsid
                        Text(
                            if (savedSsid.isNotBlank()) "Saved SSID: $savedSsid" else "No saved credentials — set Wi-Fi Creds above",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (savedSsid.isNotBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
                        )
                        pushAllResultMsg?.let { msg ->
                            Text(msg, color = if (msg.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        pushMqttAllResultMsg?.let { msg ->
                            Text(msg, color = if (msg.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
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
                        val ssid = settings.siteWifiSsid
                        val psk = settings.siteWifiPsk
                        if (ssid.isBlank() || psk.isBlank()) {
                            scannerPushState = scannerPushState + (scanner.mac to "Setup Wi-Fi Creds first ↑")
                            return@ScannerCard
                        }
                        pushingMac = scanner.mac
                        scannerPushState = scannerPushState + (scanner.mac to "Pushing Wi-Fi...")
                        pushWifiToScanner(scanner.ip, ssid, psk) { success, msg ->
                            val result = if (success) "✓ Wi-Fi Pushed" else "Wi-Fi Failed: $msg"
                            scannerPushState = scannerPushState + (scanner.mac to result)
                            if (pushingMac == scanner.mac) pushingMac = null
                        }
                    },
                    onPushMqtt = {
                        pushingMac = scanner.mac
                        scannerPushState = scannerPushState + (scanner.mac to "Pushing MQTT...")
                        pushMqttToScanner(scanner.ip) { success, msg ->
                            val result = if (success) "✓ MQTT Pushed" else "MQTT Failed: $msg"
                            scannerPushState = scannerPushState + (scanner.mac to result)
                            if (pushingMac == scanner.mac) pushingMac = null
                        }
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
                    Text(modelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

@Composable
fun ScannerCard(
    scanner: DiscoveredScanner,
    isRegistered: Boolean,
    isPushing: Boolean = false,
    pushResult: String? = null,
    savedSsid: String = "",
    onProvision: () -> Unit,
    onPushMqtt: () -> Unit,
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Button 1: Push Wi-Fi
                Button(
                    onClick = onProvision,
                    modifier = Modifier.weight(1f),
                    enabled = !isPushing,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    if (isPushing && pushResult?.contains("Wi-Fi") == true) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Wifi, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Wi-Fi", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Button 2: Push MQTT
                Button(
                    onClick = onPushMqtt,
                    modifier = Modifier.weight(1f),
                    enabled = !isPushing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    if (isPushing && pushResult?.contains("MQTT") == true) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onSecondary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.SettingsInputAntenna, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("MQTT", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Button 3: Register
                if (!isRegistered) {
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

// ─── Tab 3: Zones ───────────────────────────────────────────────────────────

@Composable
fun ZonesTab() {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var apiZones by remember { mutableStateOf<List<ApiService.ApiZone>>(emptyList()) }
    var registeredScanners by remember { mutableStateOf<List<ApiService.ApiScanner>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Create zone dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var newZoneName by remember { mutableStateOf("") }
    var newZoneDesc by remember { mutableStateOf("") }

    // Rename zone dialog
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameZone by remember { mutableStateOf<ApiService.ApiZone?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameDescText by remember { mutableStateOf("") }

    // Delete confirmation
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteZoneTarget by remember { mutableStateOf<ApiService.ApiZone?>(null) }

    // Assign/unassign confirmation
    var showAssignConfirm by remember { mutableStateOf(false) }
    var assignAction by remember { mutableStateOf<Triple<Int, Int, Boolean>?>(null) } // zoneId, scannerId, isAssign
    var assignLabel by remember { mutableStateOf("") }

    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshData() {
        scope.launch {
            if (!isRefreshing) isLoading = true
            isRefreshing = true
            errorMsg = null
            ApiService.configuredBaseUrl = settings.apiBaseUrl
            try {
                apiZones = ApiService.getZones()
                registeredScanners = ApiService.getScanners()
            } catch (e: Exception) {
                errorMsg = "API error: ${e.message}"
            }
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { refreshData() }

    // ── Create Zone Dialog ──
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            icon = { Icon(Icons.Default.AddLocationAlt, null) },
            title = { Text("Create Zone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newZoneName,
                        onValueChange = { newZoneName = it },
                        label = { Text("Zone Name (e.g. Warehouse A)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newZoneDesc,
                        onValueChange = { newZoneDesc = it },
                        label = { Text("Description (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newZoneName.isNotBlank()) {
                        scope.launch {
                            try {
                                ApiService.createZone(newZoneName.trim(), newZoneDesc.trim().ifBlank { null })
                                newZoneName = ""; newZoneDesc = ""
                                showAddDialog = false
                                refreshData()
                            } catch (e: Exception) { errorMsg = "Create failed: ${e.message}" }
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Rename Zone Dialog ──
    if (showRenameDialog && renameZone != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text("Rename Zone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Zone Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = renameDescText,
                        onValueChange = { renameDescText = it },
                        label = { Text("Description (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank()) {
                        scope.launch {
                            try {
                                ApiService.updateZone(renameZone!!.id, renameText.trim(), renameDescText.trim().ifBlank { null })
                                showRenameDialog = false
                                refreshData()
                            } catch (e: Exception) { errorMsg = "Rename failed: ${e.message}" }
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Delete Confirmation ──
    if (showDeleteConfirm && deleteZoneTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Zone?") },
            text = {
                Text("Are you sure you want to delete \"${deleteZoneTarget!!.zoneName}\"? This will unassign all scanners from this zone. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                ApiService.deleteZone(deleteZoneTarget!!.id)
                                showDeleteConfirm = false
                                refreshData()
                            } catch (e: Exception) { errorMsg = "Delete failed: ${e.message}" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // ── Assign/Unassign Confirmation ──
    if (showAssignConfirm && assignAction != null) {
        val (zId, sId, isAssign) = assignAction!!
        AlertDialog(
            onDismissRequest = { showAssignConfirm = false },
            icon = { Icon(if (isAssign) Icons.Default.LinkOff else Icons.Default.Link, null) },
            title = { Text(if (isAssign) "Assign Scanner?" else "Remove Scanner?") },
            text = { Text(assignLabel) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            if (isAssign) ApiService.assignScannerToZone(zId, sId)
                            else ApiService.unassignScannerFromZone(zId, sId)
                            showAssignConfirm = false
                            refreshData()
                        } catch (e: Exception) { errorMsg = "Failed: ${e.message}" }
                    }
                }) { Text(if (isAssign) "Assign" else "Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showAssignConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            if (settings.apiBaseUrl.isNotBlank()) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add Zone") }
                )
            }
        }
    ) { padding ->
        if (settings.apiBaseUrl.isBlank()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("API not configured", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Double-tap the Configurator title\nto set the API URL.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (apiZones.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Map, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("No zones yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Zones logically group your scanners.\nTap + to create your first zone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                errorMsg?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { refreshData() }) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh")
                }
            }
        } else {
            @OptIn(ExperimentalMaterial3Api::class)
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { refreshData() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("Zones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    }
                errorMsg?.let { msg ->
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                items(apiZones, key = { it.id }) { zone ->
                    ApiZoneCard(
                        zone = zone,
                        allScanners = registeredScanners,
                        onRename = {
                            renameZone = zone
                            renameText = zone.zoneName
                            renameDescText = zone.description ?: ""
                            showRenameDialog = true
                        },
                        onDelete = {
                            deleteZoneTarget = zone
                            showDeleteConfirm = true
                        },
                        onAssign = { scannerId, scannerLabel ->
                            assignAction = Triple(zone.id, scannerId, true)
                            assignLabel = "Assign \"$scannerLabel\" to zone \"${zone.zoneName}\"?"
                            showAssignConfirm = true
                        },
                        onUnassign = { scannerId, scannerLabel ->
                            assignAction = Triple(zone.id, scannerId, false)
                            assignLabel = "Remove \"$scannerLabel\" from zone \"${zone.zoneName}\"?"
                            showAssignConfirm = true
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
}

@Composable
fun ApiZoneCard(
    zone: ApiService.ApiZone,
    allScanners: List<ApiService.ApiScanner>,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAssign: (Int, String) -> Unit,
    onUnassign: (Int, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val assignedIds = zone.scanners.map { it.id }.toSet()
    val unassignedScanners = allScanners.filter { it.id !in assignedIds }
    var showAddDropdown by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(zone.zoneName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        zone.description?.let {
                            if (it.isNotBlank()) Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle scanners")
                    }
                }
            }

            // ── Assigned Scanners (Chips) ──
            if (zone.scanners.isEmpty()) {
                Text("No scanners assigned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            } else {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    zone.scanners.forEach { scanner ->
                        val label = scanner.name ?: scanner.macId
                        InputChip(
                            selected = true,
                            onClick = { onUnassign(scanner.id, label) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    if (scanner.type == "pi") Icons.Default.Router else Icons.Default.Sensors,
                                    null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }

            // ── Expanded: Add Scanner + Delete ──
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Scanner Button
                    Box {
                        FilledTonalButton(
                            onClick = { showAddDropdown = true },
                            enabled = unassignedScanners.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (unassignedScanners.isEmpty()) "All assigned" else "Add Scanner")
                        }
                        DropdownMenu(
                            expanded = showAddDropdown,
                            onDismissRequest = { showAddDropdown = false }
                        ) {
                            unassignedScanners.forEach { scanner ->
                                val label = scanner.name ?: scanner.macId
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text("${scanner.macId} • ${scanner.type ?: "?"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (scanner.type == "pi") Icons.Default.Router else Icons.Default.Sensors,
                                            null, modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showAddDropdown = false
                                        onAssign(scanner.id, label)
                                    }
                                )
                            }
                        }
                    }

                    // Delete Zone
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete Zone")
                    }
                }
            }
        }
    }
}

// ─── Tab 4: Assets ──────────────────────────────────────────────────────────

@Composable
fun AssetsTab() {
    val beacons by ScanRepository.beacons.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    var filter by remember { mutableStateOf("") }

    // Registered assets from API
    var registeredAssets by remember { mutableStateOf<List<ApiService.ApiAsset>>(emptyList()) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var registerMac by remember { mutableStateOf("") }
    var registerName by remember { mutableStateOf("") }
    var registerError by remember { mutableStateOf<String?>(null) }

    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshAssets() {
        scope.launch {
            isRefreshing = true
            ApiService.configuredBaseUrl = settings.apiBaseUrl
            registeredAssets = try { ApiService.getAssets() } catch (e: Exception) { emptyList() }
            ScanRepository.setRegisteredAssets(registeredAssets)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { refreshAssets() }

    val registeredMacs = remember(registeredAssets) {
        registeredAssets.associateBy { it.bluetoothId.uppercase() }
    }

    val filtered = remember(beacons, filter) {
        if (filter.isBlank()) beacons
        else beacons.filter {
            it.mac.contains(filter, ignoreCase = true) ||
            it.name?.contains(filter, ignoreCase = true) == true
        }
    }

    // Register dialog
    if (showRegisterDialog) {
        AlertDialog(
            onDismissRequest = { showRegisterDialog = false; registerError = null },
            icon = { Icon(Icons.Default.AppRegistration, null) },
            title = { Text("Register Beacon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(registerMac, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
                    OutlinedTextField(
                        value = registerName,
                        onValueChange = { registerName = it },
                        label = { Text("Friendly Name (e.g. Forklift #3)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    registerError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            ApiService.registerAsset(registerMac, registerName.trim().ifBlank { null })
                            showRegisterDialog = false
                            registerName = ""; registerError = null
                            refreshAssets()
                        } catch (e: Exception) {
                            registerError = e.message ?: "Registration failed"
                        }
                    }
                }) { Text("Register") }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false; registerError = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Assets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
        // Search bar
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Search by MAC or name...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) {
                    Icon(Icons.Default.Clear, null)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(28.dp)
        )

        if (beacons.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.BluetoothSearching, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("No beacons scanned yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Start the BLE scanner from the main tab.\nActive beacons will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            @OptIn(ExperimentalMaterial3Api::class)
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { refreshAssets() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            "${filtered.size} of ${beacons.size} beacons",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                items(filtered, key = { it.mac }) { beacon ->
                    val registered = registeredMacs[beacon.mac.uppercase()]
                    BeaconAssetCard(
                        beacon = beacon,
                        registeredAsset = registered,
                        onRegister = {
                            registerMac = beacon.mac
                            registerName = beacon.name ?: ""
                            showRegisterDialog = true
                        },
                        onDelete = { assetId ->
                            scope.launch {
                                try { ApiService.deleteAsset(assetId); refreshAssets() }
                                catch (_: Exception) {}
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
}

@Composable
fun BeaconAssetCard(
    beacon: BeaconData,
    registeredAsset: ApiService.ApiAsset?,
    onRegister: () -> Unit,
    onDelete: (Int) -> Unit
) {
    val rssiColor = when {
        beacon.rssi > -60 -> MaterialTheme.colorScheme.primary
        beacon.rssi > -80 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val isRegistered = registeredAsset != null

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRegistered) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRegistered) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                        null,
                        tint = if (isRegistered) MaterialTheme.colorScheme.onTertiaryContainer
                               else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        registeredAsset?.assetName ?: beacon.name ?: beacon.mac,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        beacon.mac,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontFamily = FontFamily.Monospace
                    )
                    beacon.beaconType?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${beacon.rssi} dBm",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = rssiColor
                )
                if (isRegistered) {
                    Text("Registered", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                } else {
                    TextButton(onClick = onRegister, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Register", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

