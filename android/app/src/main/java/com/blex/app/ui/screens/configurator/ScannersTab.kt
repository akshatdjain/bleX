package com.blegod.app.ui.screens.configurator

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items
import com.blegod.app.data.ApiService
import com.blegod.app.data.DiscoveredScanner
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                letterSpacing = 1.5.sp
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
                        textAlign = TextAlign.Center
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
