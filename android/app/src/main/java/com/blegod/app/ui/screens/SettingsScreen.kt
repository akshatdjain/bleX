package com.blegod.app.ui.screens

import android.util.Log
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.blegod.app.AppConfig
import com.blegod.app.BatteryMonitor
import com.blegod.app.ServiceHealth
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSettingsSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }

    // MQTT
    var host by remember { mutableStateOf(settings.mqttHost) }
    var port by remember { mutableStateOf(settings.mqttPort.toString()) }
    var topicPrefix by remember { mutableStateOf(settings.mqttTopicPrefix) }
    var qos by remember { mutableIntStateOf(settings.mqttQos) }
    var tlsEnabled by remember { mutableStateOf(settings.mqttTlsEnabled) }
    var username by remember { mutableStateOf(settings.mqttUsername) }
    var password by remember { mutableStateOf(settings.mqttPassword) }
    var keepAlive by remember { mutableStateOf(settings.mqttKeepAlive.toString()) }
    var connTimeout by remember { mutableStateOf(settings.mqttConnectionTimeout.toString()) }
    var scanInterval by remember { mutableStateOf(settings.scanIntervalMs.toString()) }
    var scanDuration by remember { mutableStateOf(settings.scanDurationMs.toString()) }

    // New v1.1 settings
    var scanPowerMode by remember { mutableStateOf(settings.scanPowerMode) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var scannerMacLabel by remember { mutableStateOf(settings.scannerMacLabel) }
    var payloadTemplate by remember { mutableStateOf(settings.mqttPayloadTemplate) }

    // Broker settings
    var brokerEnabled by remember { mutableStateOf(settings.brokerEnabled) }
    var brokerPort by remember { mutableStateOf(settings.brokerPort.toString()) }
    var brokerUsername by remember { mutableStateOf(settings.brokerUsername) }
    var brokerPassword by remember { mutableStateOf(settings.brokerPassword) }

    // Remote server settings
    var remoteHost by remember { mutableStateOf(settings.remoteHost) }
    var remotePort by remember { mutableStateOf(settings.remotePort.toString()) }
    var remoteTlsEnabled by remember { mutableStateOf(settings.remoteTlsEnabled) }
    var remoteTlsStrict by remember { mutableStateOf(settings.remoteTlsStrict) }
    var remoteUseWebSocket by remember { mutableStateOf(settings.remoteUseWebSocket) }
    var remoteUsername by remember { mutableStateOf(settings.remoteUsername) }
    var remotePassword by remember { mutableStateOf(settings.remotePassword) }
    var remoteCaCertUri by remember { mutableStateOf(settings.remoteCaCertUri) }
    var bridgeTopicFilter by remember { mutableStateOf(settings.bridgeTopicFilter) }

    var showPassword by remember { mutableStateOf(false) }
    var showSavedSnackbar by remember { mutableStateOf(false) }

    val serviceStatus by ScanRepository.serviceStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(showSavedSnackbar) {
        if (showSavedSnackbar) {
            snackbarHostState.showSnackbar("Settings saved and applied")
            showSavedSnackbar = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings", style = MaterialTheme.typography.titleLarge) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (settings.mqttHost != host) settings.mqttHost = host
                    val parsedMqttPort = port.toIntOrNull() ?: 1883
                    if (settings.mqttPort != parsedMqttPort) settings.mqttPort = parsedMqttPort
                    if (settings.mqttTopicPrefix != topicPrefix) settings.mqttTopicPrefix = topicPrefix
                    if (settings.mqttQos != qos) settings.mqttQos = qos
                    if (settings.mqttTlsEnabled != tlsEnabled) settings.mqttTlsEnabled = tlsEnabled
                    if (settings.mqttUsername != username) settings.mqttUsername = username
                    if (settings.mqttPassword != password) settings.mqttPassword = password
                    val parsedKeepAlive = keepAlive.toIntOrNull() ?: 30
                    if (settings.mqttKeepAlive != parsedKeepAlive) settings.mqttKeepAlive = parsedKeepAlive
                    val parsedConnTimeout = connTimeout.toIntOrNull() ?: 10
                    if (settings.mqttConnectionTimeout != parsedConnTimeout) settings.mqttConnectionTimeout = parsedConnTimeout
                    val parsedScanInterval = scanInterval.toLongOrNull() ?: 2500L
                    if (settings.scanIntervalMs != parsedScanInterval) settings.scanIntervalMs = parsedScanInterval
                    val parsedScanDuration = scanDuration.toLongOrNull() ?: 2000L
                    if (settings.scanDurationMs != parsedScanDuration) settings.scanDurationMs = parsedScanDuration
                    if (settings.scanPowerMode != scanPowerMode) settings.scanPowerMode = scanPowerMode
                    if (settings.themeMode != themeMode) settings.themeMode = themeMode
                    if (settings.scannerMacLabel != scannerMacLabel) settings.scannerMacLabel = scannerMacLabel
                    if (settings.mqttPayloadTemplate != payloadTemplate) settings.mqttPayloadTemplate = payloadTemplate
                    if (settings.brokerEnabled != brokerEnabled) settings.brokerEnabled = brokerEnabled
                    val parsedBrokerPort = brokerPort.toIntOrNull() ?: 1883
                    if (settings.brokerPort != parsedBrokerPort) settings.brokerPort = parsedBrokerPort
                    if (settings.brokerUsername != brokerUsername) settings.brokerUsername = brokerUsername
                    if (settings.brokerPassword != brokerPassword) settings.brokerPassword = brokerPassword
                    if (settings.remoteHost != remoteHost) settings.remoteHost = remoteHost
                    val parsedRemotePort = remotePort.toIntOrNull() ?: 443
                    if (settings.remotePort != parsedRemotePort) settings.remotePort = parsedRemotePort
                    if (settings.remoteTlsEnabled != remoteTlsEnabled) settings.remoteTlsEnabled = remoteTlsEnabled
                    if (settings.remoteTlsStrict != remoteTlsStrict) settings.remoteTlsStrict = remoteTlsStrict
                    if (settings.remoteUseWebSocket != remoteUseWebSocket) settings.remoteUseWebSocket = remoteUseWebSocket
                    if (settings.remoteUsername != remoteUsername) settings.remoteUsername = remoteUsername
                    if (settings.remotePassword != remotePassword) settings.remotePassword = remotePassword
                    if (settings.remoteCaCertUri != remoteCaCertUri) settings.remoteCaCertUri = remoteCaCertUri
                    if (settings.bridgeTopicFilter != bridgeTopicFilter) settings.bridgeTopicFilter = bridgeTopicFilter
                    showSavedSnackbar = true
                    onSettingsSaved()
                },
                icon = { Icon(Icons.Default.Save, "Save") },
                text = { Text("Save") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ── Appearance ──────────────────────────────────────
            SectionHeader(icon = Icons.Default.Palette, title = "Appearance")

            var themeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = themeExpanded,
                onExpandedChange = { themeExpanded = !themeExpanded }
            ) {
                OutlinedTextField(
                    value = when (themeMode) {
                        "DARK" -> "Dark"
                        "LIGHT" -> "Light"
                        else -> "System Default"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Theme") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    leadingIcon = { Icon(Icons.Default.DarkMode, null) }
                )
                ExposedDropdownMenu(
                    expanded = themeExpanded,
                    onDismissRequest = { themeExpanded = false }
                ) {
                    listOf("SYSTEM" to "System Default", "DARK" to "Dark", "LIGHT" to "Light").forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { themeMode = mode; themeExpanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Publishing Settings ──────────────────────────────
            SectionHeader(icon = Icons.Default.Publish, title = "Publishing")

            OutlinedTextField(
                value = topicPrefix, onValueChange = { topicPrefix = it },
                label = { Text("Topic Prefix") }, singleLine = true,
                placeholder = { Text("blegod/scans") },
                modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Topic, null) }
            )
            Spacer(Modifier.height(12.dp))

            // QoS dropdown
            var qosExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = qosExpanded, onExpandedChange = { qosExpanded = !qosExpanded }) {
                OutlinedTextField(
                    value = "QoS $qos — ${qosLabel(qos)}", onValueChange = {},
                    readOnly = true, label = { Text("Quality of Service") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qosExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    leadingIcon = { Icon(Icons.Default.VerifiedUser, null) }
                )
                ExposedDropdownMenu(expanded = qosExpanded, onDismissRequest = { qosExpanded = false }) {
                    listOf(0, 1, 2).forEach { level ->
                        DropdownMenuItem(
                            text = { Text("QoS $level — ${qosLabel(level)}") },
                            onClick = { qos = level; qosExpanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Direct MQTT (only when broker is disabled) ───────
            if (!brokerEnabled) {
                SectionHeader(icon = Icons.Default.Cloud, title = "Direct MQTT Connection")

                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text("Broker Host") }, placeholder = { Text("mqtt.example.com") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Dns, null) }
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Tag, null) }
                )
                Spacer(Modifier.height(12.dp))

                ToggleCard(
                    icon = Icons.Default.Lock,
                    title = "TLS / SSL",
                    subtitle = "Encrypt MQTT traffic",
                    checked = tlsEnabled,
                    onCheckedChange = { tlsEnabled = it }
                )
                Spacer(Modifier.height(16.dp))

                SectionHeader(icon = Icons.Default.Person, title = "Authentication (Optional)")

                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.AccountCircle, null) }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Key, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                "Toggle password"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                )
                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = keepAlive, onValueChange = { keepAlive = it.filter { c -> c.isDigit() } },
                        label = { Text("Keep Alive (s)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = connTimeout, onValueChange = { connTimeout = it.filter { c -> c.isDigit() } },
                        label = { Text("Timeout (s)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(24.dp))
            }


            // ── Local Broker ─────────────────────────────────────
            SectionHeader(icon = Icons.Default.Hub, title = "Local Broker")

            ToggleCard(
                icon = Icons.Default.Hub,
                title = "Embedded Broker",
                subtitle = "Run MQTT broker on this device",
                checked = brokerEnabled,
                onCheckedChange = { brokerEnabled = it }
            )

            if (brokerEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = brokerPort, onValueChange = { brokerPort = it.filter { c -> c.isDigit() } },
                    label = { Text("Broker Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Tag, null) }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = brokerUsername, onValueChange = { brokerUsername = it },
                    label = { Text("Broker Username (Optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.AccountCircle, null) }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = brokerPassword, onValueChange = { brokerPassword = it },
                    label = { Text("Broker Password (Optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Key, null) },
                    visualTransformation = PasswordVisualTransformation()
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Remote Server (Upstream) ─────────────────────────
            SectionHeader(icon = Icons.Default.CloudUpload, title = "Remote Server (Upstream)")

            OutlinedTextField(
                value = remoteHost, onValueChange = { remoteHost = it },
                label = { Text("Domain / Host") }, placeholder = { Text("mqtt.yourdomain.com") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Language, null) }
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = remotePort, onValueChange = { remotePort = it.filter { c -> c.isDigit() } },
                label = { Text("Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Tag, null) }
            )
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleCard(
                    icon = Icons.Default.Lock,
                    title = "TLS",
                    subtitle = "Encrypt traffic",
                    checked = remoteTlsEnabled,
                    onCheckedChange = { remoteTlsEnabled = it }
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleCard(
                    icon = Icons.Default.Webhook,
                    title = "WebSocket",
                    subtitle = "Use WSS protocol",
                    checked = remoteUseWebSocket,
                    onCheckedChange = { remoteUseWebSocket = it }
                )
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = remoteUsername, onValueChange = { remoteUsername = it },
                label = { Text("Remote Username") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.AccountCircle, null) }
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = remotePassword, onValueChange = { remotePassword = it },
                label = { Text("Remote Password") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Key, null) },
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(16.dp))

            // ── CA Certificate Picker ────────────────────────────
            if (remoteTlsEnabled) {
                Text(
                    "Security Configuration",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                ToggleCard(
                    icon = Icons.Default.Security,
                    title = "Strict Verification",
                    subtitle = if (remoteCaCertUri.isNotEmpty()) "Forced by custom CA" else "Validate certificates",
                    checked = remoteTlsStrict || remoteCaCertUri.isNotEmpty(),
                    enabled = remoteCaCertUri.isEmpty(), // If cert is present, strict is forced
                    onCheckedChange = { remoteTlsStrict = it }
                )
                Spacer(Modifier.height(12.dp))

                val pickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri: android.net.Uri? ->
                    uri?.let {
                        try {
                            context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            remoteCaCertUri = it.toString()
                        } catch (e: Exception) {
                            Log.e("Settings", "Failed to grant URI permission", e)
                            remoteCaCertUri = it.toString()
                        }
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = when {
                            remoteCaCertUri.isNotEmpty() -> MaterialTheme.colorScheme.surface
                            remoteTlsStrict -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                when {
                                    remoteCaCertUri.isNotEmpty() -> Icons.Default.VerifiedUser
                                    remoteTlsStrict -> Icons.Default.GppGood
                                    else -> Icons.Default.Warning
                                },
                                "Status",
                                tint = when {
                                    remoteCaCertUri.isNotEmpty() -> MaterialTheme.colorScheme.primary
                                    remoteTlsStrict -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        remoteCaCertUri.isNotEmpty() -> "Custom CA Certificate Active"
                                        remoteTlsStrict -> "Strict Mode (System CA)"
                                        else -> "Using Insecure 'Trust All' Mode"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    when {
                                        remoteCaCertUri.isNotEmpty() -> "Only connections verified by this CA will be allowed."
                                        remoteTlsStrict -> "Compatible with official domains with valid certificates."
                                        else -> "This ignores all SSL validity checks (vulnerable to MitM)."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        if (remoteCaCertUri.isNotEmpty()) {
                            Text(
                                "URI: $remoteCaCertUri",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (remoteCaCertUri.isNotEmpty()) {
                                TextButton(onClick = { remoteCaCertUri = "" }) {
                                    Text("Clear Cert", color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Button(
                                onClick = { pickerLauncher.launch(arrayOf("*/*")) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Upload ca.crt")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = bridgeTopicFilter, onValueChange = { bridgeTopicFilter = it },
                label = { Text("Bridge Topic Filter") }, placeholder = { Text("# (forward all)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.FilterAlt, null) },
                supportingText = { Text("Which local topics to forward upstream") }
            )

            Spacer(Modifier.height(24.dp))

            // ── BLE Scanning ─────────────────────────────────────
            SectionHeader(icon = Icons.Default.Bluetooth, title = "BLE Scanning")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = scanInterval, onValueChange = { scanInterval = it.filter { c -> c.isDigit() } },
                    label = { Text("Interval (ms)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = scanDuration, onValueChange = { scanDuration = it.filter { c -> c.isDigit() } },
                    label = { Text("Duration (ms)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Scan Power Mode
            Text("Scan Power Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("LOW_POWER" to "Low Power", "BALANCED" to "Balanced", "LOW_LATENCY" to "Low Latency")
                    .forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = scanPowerMode == mode,
                            onClick = { scanPowerMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
            }

            Spacer(Modifier.height(24.dp))

            // ── Scanner Identity ─────────────────────────────────
            SectionHeader(icon = Icons.Default.Fingerprint, title = "Scanner Identity")

            OutlinedTextField(
                value = scannerMacLabel,
                onValueChange = { scannerMacLabel = it },
                label = { Text("Scanner MAC Label") },
                placeholder = { Text("Leave empty to use Device ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Label, null) },
                supportingText = {
                    Text("Current ID: ${AppConfig.getDeviceId(context)}")
                }
            )

            Spacer(Modifier.height(24.dp))

            // ── MQTT Payload Template ────────────────────────────
            SectionHeader(icon = Icons.Default.Code, title = "MQTT Payload Template")

            OutlinedTextField(
                value = payloadTemplate,
                onValueChange = { payloadTemplate = it },
                label = { Text("JSON Template") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 15,
                supportingText = {
                    Text("Available: \${SCANNER_MAC}, \${BEACON_MAC}, \${RSSI}, \${TX_POWER}, \${TIMESTAMP_UTC}, \${BEACON_TYPE}, \${IBEACON_UUID}, \${IBEACON_MAJOR}, \${IBEACON_MINOR}, \${NAME}")
                }
            )
            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { payloadTemplate = SettingsManager.DEFAULT_PAYLOAD_TEMPLATE }) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset to Default")
            }

            Spacer(Modifier.height(24.dp))

            // ── Battery Stats ────────────────────────────────────
            SectionHeader(icon = Icons.Default.BatteryStd, title = "Battery")

            // Read battery once per recomposition — BatteryMonitor is fast, reads broadcast
            val battery = remember(serviceStatus.batteryLevel, serviceStatus.isCharging) {
                BatteryMonitor.getStats(context)
            }
            val drainRate = remember(serviceStatus.batteryLevel) {
                BatteryMonitor.getDrainRatePerHour()
            }

            val batteryColor = when {
                battery.level > 50 -> MaterialTheme.colorScheme.primary
                battery.level > 20 -> MaterialTheme.colorScheme.tertiary
                else               -> MaterialTheme.colorScheme.error
            }
            val progressColor = if (battery.isCharging) MaterialTheme.colorScheme.primary else batteryColor

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Big percentage header — no animation wrapper, just reads value
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Battery Level", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${battery.level}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = batteryColor
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { battery.level / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))

                    InfoRow("Status",         if (battery.isCharging) "Charging ⚡" else "Discharging")
                    InfoRow("Plug",           battery.plugType)
                    InfoRow("Voltage",        "${battery.voltage} V")
                    InfoRow("Temperature",    "${battery.temperature}°C")
                    InfoRow("Health",         battery.health)
                    InfoRow(
                        "Drain Rate",
                        if (battery.isCharging || drainRate <= 0f) "—"
                        else "%.1f%% / hr".format(drainRate)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Service Health ────────────────────────────────────
            SectionHeader(icon = Icons.Default.MonitorHeart, title = "Service Health")

            // Memory — read fresh each composition, not tied to serviceStatus timing
            val runtime = Runtime.getRuntime()
            val usedMb  = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val totalMb = runtime.maxMemory() / (1024 * 1024)

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            ) {
                // NO AnimatedContent — each Text reads the field directly so only
                // the individual text recomposes, no layout thrash / no blink
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    InfoRowWithIcon(
                        Icons.Default.Timer, "Uptime",
                        ServiceHealth.formatUptime(serviceStatus.uptimeMs())
                    )
                    InfoRowWithIcon(
                        Icons.Default.Radar, "BLE Scanner",
                        if (serviceStatus.isScanning) "Running" else "Stopped",
                        if (serviceStatus.isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    InfoRowWithIcon(
                        Icons.Default.CloudQueue, "MQTT",
                        if (serviceStatus.isMqttConnected) "Connected" else "Disconnected",
                        if (serviceStatus.isMqttConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    InfoRow("Scan Cycles",           "${serviceStatus.scanCycleCount}")
                    InfoRow("Total Beacons Seen",    "${serviceStatus.totalBeaconsScanned}")
                    InfoRow("Messages Published",    "${serviceStatus.messagesPublished}")
                    InfoRow("Messages Failed",       "${serviceStatus.messagesFailed}")

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    InfoRowWithIcon(
                        Icons.Default.Memory, "Memory",
                        "$usedMb MB / $totalMb MB",
                        when {
                            usedMb.toFloat() / totalMb < 0.6f -> MaterialTheme.colorScheme.primary
                            usedMb.toFloat() / totalMb < 0.85f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Device Info ──────────────────────────────────────
            SectionHeader(icon = Icons.Default.Info, title = "Device Info")

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    InfoRow("Device ID",  AppConfig.getDeviceId(context))
                    InfoRow("Android",    "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    InfoRow("Model",      "${Build.MANUFACTURER} ${Build.MODEL}")
                    InfoRow("App Version","3.2.0")
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                "engineered in silence by akshat",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(100.dp))
        }
    }
}

// ── Status Flow Extensions ───────────────────────────────────

private fun com.blegod.app.data.ServiceStatus.uptimeMs(): Long {
    return System.currentTimeMillis() - startTime
}

// ── Components ───────────────────────────────────────────────

@Composable
private fun InfoRowWithIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun ToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f), 
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null, enabled = enabled)
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun qosLabel(qos: Int): String = when (qos) {
    0 -> "Fire & forget"
    1 -> "At least once"
    2 -> "Exactly once"
    else -> "Unknown"
}
