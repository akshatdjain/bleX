package com.blegod.app.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.blegod.app.AppConfig
import com.blegod.app.BatteryMonitor
import com.blegod.app.ServiceHealth
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager

// ── Settings Categories ───────────────────────────────────────

enum class SettingsCategory(
    val label: String,
    val icon: ImageVector,
    val subtitle: String
) {
    APPEARANCE("Appearance", Icons.Default.Palette, "Theme & display"),
    PUBLISHING("Publishing", Icons.Default.Publish, "Topic prefix, QoS"),
    LOCAL_BROKER("Local Broker", Icons.Default.Hub, "Embedded MQTT broker"),
    REMOTE_SERVER("Remote Server", Icons.Default.CloudUpload, "Upstream & API endpoint"),
    BEACON_DISCOVERY("Beacon Discovery", Icons.Default.Radar, "Scan interval & power"),
    IDENTITY_PAYLOAD("Identity & Payload", Icons.Default.Fingerprint, "Scanner MAC & MQTT template"),
    BATTERY("Battery", Icons.Default.BatteryStd, "Power & charging info"),
    SERVICE_HEALTH("Service Health", Icons.Default.MonitorHeart, "Uptime & diagnostics"),
    DEVICE_INFO("Device Info", Icons.Default.Info, "Hardware & software"),
    NOTIFICATIONS("Notifications", Icons.Default.NotificationsActive, "Manage Android app alerts"),
    LOGS("Logs", Icons.Default.Terminal, "Toggle log visibility")
}

// ── Root SettingsScreen ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSettingsSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600

    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(
        if (isTablet) SettingsCategory.APPEARANCE else null
    ) }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isTablet) {
            // ── TABLET: Split-view ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(260.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ) {
                    SettingsCategoryList(
                        selected = selectedCategory,
                        onSelect = { selectedCategory = it }
                    )
                }
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    AnimatedContent(
                        targetState = selectedCategory,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        label = "SettingsDetail"
                    ) { category ->
                        if (category != null) {
                            SettingsDetailPanel(
                                category = category,
                                settings = settings,
                                onSettingsSaved = onSettingsSaved
                            )
                        }
                    }
                }
            }
        } else {
            // ── PHONE: Category list OR detail page ─────────────
            AnimatedContent(
                targetState = selectedCategory,
                transitionSpec = {
                    if (targetState != null) {
                        slideInHorizontally { it / 3 } + fadeIn() togetherWith
                            slideOutHorizontally { -it / 3 } + fadeOut()
                    } else {
                        slideInHorizontally { -it / 3 } + fadeIn() togetherWith
                            slideOutHorizontally { it / 3 } + fadeOut()
                    }
                },
                label = "SettingsNavigation",
                modifier = Modifier.fillMaxSize().padding(padding)
            ) { category ->
                if (category == null) {
                    SettingsCategoryList(
                        selected = null,
                        onSelect = { selectedCategory = it }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Let the system/TopAppBar handle the back button, but we catch the gesture
                        BackHandler {
                            selectedCategory = null
                        }
                        SettingsDetailPanel(
                            category = category,
                            settings = settings,
                            onSettingsSaved = onSettingsSaved
                        )
                    }
                }
            }
        }
    }
}

// ── Category List (Modern Android Style) ──────────────────────

@Composable
private fun SettingsCategoryList(
    selected: SettingsCategory?,
    onSelect: (SettingsCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // Minimal vertical padding
            .padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Removed duplicate "Settings" big text, the TopAppBar handles it

        SettingsCategory.entries.forEach { category ->
            val isSelected = category == selected
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp), // Padding constraint moved to item level
                shape = RoundedCornerShape(24.dp),
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent, // We'll let the background handle color, but items will just have large padding
                onClick = { onSelect(category) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 20.dp), // Taller items
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Icon inside a circle background
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                category.icon, null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            category.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            category.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Add a tiny chevron for non-tablet screens
                    if (!LocalConfiguration.current.let { it.screenWidthDp >= 600 }) {
                        Icon(
                            Icons.Default.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Detail Panel Router ────────────────────────────────────────

@Composable
private fun SettingsDetailPanel(
    category: SettingsCategory,
    settings: SettingsManager,
    onSettingsSaved: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Removed duplicate title, category label can replace the TopAppBar title,
        // but since we want it edge to edge we just display the panel content.

        when (category) {
            SettingsCategory.APPEARANCE -> AppearancePanel(settings)
            SettingsCategory.PUBLISHING -> PublishingPanel(settings, onSettingsSaved)
            SettingsCategory.LOCAL_BROKER -> LocalBrokerPanel(settings, onSettingsSaved)
            SettingsCategory.REMOTE_SERVER -> RemoteServerPanel(settings, onSettingsSaved)
            SettingsCategory.BEACON_DISCOVERY -> BeaconDiscoveryPanel(settings, onSettingsSaved)
            SettingsCategory.IDENTITY_PAYLOAD -> IdentityPayloadPanel(settings, onSettingsSaved)
            SettingsCategory.BATTERY -> BatteryPanel()
            SettingsCategory.SERVICE_HEALTH -> ServiceHealthPanel()
            SettingsCategory.DEVICE_INFO -> DeviceInfoPanel()
            SettingsCategory.NOTIFICATIONS -> NotificationsPanel()
            SettingsCategory.LOGS -> LogsPanel(settings)
        }

        Spacer(Modifier.height(80.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
// ── DETAIL PANELS (auto-save) ─────────────────────────────────
// ═══════════════════════════════════════════════════════════════

// Auto-save helper: text fields save when panel is disposed
// Toggles and dropdowns save immediately on change

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearancePanel(settings: SettingsManager) {
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var themeExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = themeExpanded,
        onExpandedChange = { themeExpanded = !themeExpanded }
    ) {
        OutlinedTextField(
            value = when (themeMode) {
                "DARK" -> "Dark"; "LIGHT" -> "Light"; else -> "System Default"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            leadingIcon = { Icon(Icons.Default.DarkMode, null) }
        )
        ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
            listOf("SYSTEM" to "System Default", "DARK" to "Dark", "LIGHT" to "Light").forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        themeMode = mode; themeExpanded = false
                        settings.themeMode = mode // ← auto-save
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishingPanel(settings: SettingsManager, onSaved: () -> Unit) {
    var topicPrefix by remember { mutableStateOf(settings.mqttTopicPrefix) }
    var qos by remember { mutableIntStateOf(settings.mqttQos) }

    // Direct MQTT fields (when broker disabled)
    var host by remember { mutableStateOf(settings.mqttHost) }
    var port by remember { mutableStateOf(settings.mqttPort.toString()) }
    var tlsEnabled by remember { mutableStateOf(settings.mqttTlsEnabled) }
    var username by remember { mutableStateOf(settings.mqttUsername) }
    var password by remember { mutableStateOf(settings.mqttPassword) }
    var keepAlive by remember { mutableStateOf(settings.mqttKeepAlive.toString()) }
    var connTimeout by remember { mutableStateOf(settings.mqttConnectionTimeout.toString()) }
    var showPassword by remember { mutableStateOf(false) }
    val brokerEnabled = settings.brokerEnabled

    // Auto-save text fields when leaving this panel
    DisposableEffect(Unit) {
        onDispose {
            settings.mqttTopicPrefix = topicPrefix
            if (!brokerEnabled) {
                settings.mqttHost = host
                settings.mqttPort = port.toIntOrNull() ?: 1883
                settings.mqttUsername = username
                settings.mqttPassword = password
                settings.mqttKeepAlive = keepAlive.toIntOrNull() ?: 30
                settings.mqttConnectionTimeout = connTimeout.toIntOrNull() ?: 10
            }
            onSaved()
        }
    }

    OutlinedTextField(
        value = topicPrefix, onValueChange = { topicPrefix = it },
        label = { Text("Topic Prefix") }, singleLine = true,
        placeholder = { Text("blegod/scans") },
        modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Topic, null) }
    )

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
                    onClick = {
                        qos = level; qosExpanded = false
                        settings.mqttQos = level // ← auto-save
                    }
                )
            }
        }
    }

    // Direct MQTT (only when broker disabled)
    if (!brokerEnabled) {
        SectionDivider("Direct MQTT Connection")

        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text("Broker Host") }, placeholder = { Text("mqtt.example.com") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Dns, null) }
        )
        OutlinedTextField(
            value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text("Port") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Tag, null) }
        )
        ToggleCard(
            icon = Icons.Default.Lock, title = "TLS / SSL",
            subtitle = "Encrypt MQTT traffic",
            checked = tlsEnabled,
            onCheckedChange = {
                tlsEnabled = it
                settings.mqttTlsEnabled = it // ← auto-save
            }
        )

        SectionDivider("Authentication (Optional)")

        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Username") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.AccountCircle, null) }
        )
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
    }
}

@Composable
private fun LocalBrokerPanel(settings: SettingsManager, onSaved: () -> Unit) {
    var brokerEnabled by remember { mutableStateOf(settings.brokerEnabled) }
    var brokerPort by remember { mutableStateOf(settings.brokerPort.toString()) }
    var brokerUsername by remember { mutableStateOf(settings.brokerUsername) }
    var brokerPassword by remember { mutableStateOf(settings.brokerPassword) }

    // Auto-save text fields on panel exit
    DisposableEffect(Unit) {
        onDispose {
            settings.brokerPort = brokerPort.toIntOrNull() ?: 1883
            settings.brokerUsername = brokerUsername
            settings.brokerPassword = brokerPassword
            onSaved()
        }
    }

    ToggleCard(
        icon = Icons.Default.Hub, title = "Embedded Broker",
        subtitle = "Run MQTT broker on this device",
        checked = brokerEnabled,
        onCheckedChange = {
            brokerEnabled = it
            settings.brokerEnabled = it // ← auto-save (major change, saves immediately)
        }
    )

    AnimatedVisibility(visible = brokerEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = brokerPort, onValueChange = { brokerPort = it.filter { c -> c.isDigit() } },
                label = { Text("Broker Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Tag, null) }
            )
            OutlinedTextField(
                value = brokerUsername, onValueChange = { brokerUsername = it },
                label = { Text("Broker Username (Optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.AccountCircle, null) }
            )
            OutlinedTextField(
                value = brokerPassword, onValueChange = { brokerPassword = it },
                label = { Text("Broker Password (Optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Key, null) },
                visualTransformation = PasswordVisualTransformation()
            )
        }
    }
}

@Composable
private fun RemoteServerPanel(settings: SettingsManager, onSaved: () -> Unit) {
    val context = LocalContext.current

    var remoteHost by remember { mutableStateOf(settings.remoteHost) }
    var remotePort by remember { mutableStateOf(settings.remotePort.toString()) }
    var remoteTlsEnabled by remember { mutableStateOf(settings.remoteTlsEnabled) }
    var remoteTlsStrict by remember { mutableStateOf(settings.remoteTlsStrict) }
    var remoteUseWebSocket by remember { mutableStateOf(settings.remoteUseWebSocket) }
    var remoteUsername by remember { mutableStateOf(settings.remoteUsername) }
    var remotePassword by remember { mutableStateOf(settings.remotePassword) }
    var remoteCaCertUri by remember { mutableStateOf(settings.remoteCaCertUri) }
    var bridgeTopicFilter by remember { mutableStateOf(settings.bridgeTopicFilter) }
    var apiBaseUrl by remember { mutableStateOf(settings.apiBaseUrl) }

    // Auto-save text fields on panel exit
    DisposableEffect(Unit) {
        onDispose {
            settings.remoteHost = remoteHost
            settings.remotePort = remotePort.toIntOrNull() ?: 443
            settings.remoteUsername = remoteUsername
            settings.remotePassword = remotePassword
            settings.remoteCaCertUri = remoteCaCertUri
            settings.bridgeTopicFilter = bridgeTopicFilter
            settings.apiBaseUrl = apiBaseUrl
            onSaved()
        }
    }

    // ── API Base URL (for Configurator) ────────────────────
    SectionDivider("API Endpoint")

    OutlinedTextField(
        value = apiBaseUrl, onValueChange = { apiBaseUrl = it },
        label = { Text("API Base URL") },
        placeholder = { Text("http://192.168.1.100:8000") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Api, null) },
        supportingText = { Text("Used by Configurator for zones, scanners, assets") }
    )

    Spacer(Modifier.height(4.dp))

    // ── MQTT Upstream ──────────────────────────────────────
    SectionDivider("MQTT Upstream")

    OutlinedTextField(
        value = remoteHost, onValueChange = { remoteHost = it },
        label = { Text("MQTT Host") }, placeholder = { Text("mqtt.yourdomain.com") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Language, null) }
    )
    OutlinedTextField(
        value = remotePort, onValueChange = { remotePort = it.filter { c -> c.isDigit() } },
        label = { Text("MQTT Port") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Tag, null) }
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleCard(
            icon = Icons.Default.Lock, title = "TLS",
            subtitle = "Encrypt traffic",
            checked = remoteTlsEnabled,
            onCheckedChange = {
                remoteTlsEnabled = it
                settings.remoteTlsEnabled = it
            }
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleCard(
            icon = Icons.Default.Webhook, title = "WebSocket",
            subtitle = "Use WSS protocol",
            checked = remoteUseWebSocket,
            onCheckedChange = {
                remoteUseWebSocket = it
                settings.remoteUseWebSocket = it
            }
        )
    }

    OutlinedTextField(
        value = remoteUsername, onValueChange = { remoteUsername = it },
        label = { Text("Remote Username") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.AccountCircle, null) }
    )
    OutlinedTextField(
        value = remotePassword, onValueChange = { remotePassword = it },
        label = { Text("Remote Password") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Key, null) },
        visualTransformation = PasswordVisualTransformation()
    )

    // CA Certificate picker (when TLS enabled)
    AnimatedVisibility(visible = remoteTlsEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionDivider("Security Configuration")

            ToggleCard(
                icon = Icons.Default.Security, title = "Strict Verification",
                subtitle = if (remoteCaCertUri.isNotEmpty()) "Forced by custom CA" else "Validate certificates",
                checked = remoteTlsStrict || remoteCaCertUri.isNotEmpty(),
                enabled = remoteCaCertUri.isEmpty(),
                onCheckedChange = {
                    remoteTlsStrict = it
                    settings.remoteTlsStrict = it
                }
            )

            val pickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: android.net.Uri? ->
                uri?.let {
                    try {
                        context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        remoteCaCertUri = it.toString()
                        settings.remoteCaCertUri = it.toString()
                    } catch (e: Exception) {
                        Log.e("Settings", "Failed to grant URI permission", e)
                        remoteCaCertUri = it.toString()
                        settings.remoteCaCertUri = it.toString()
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
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (remoteCaCertUri.isNotEmpty()) {
                            TextButton(onClick = {
                                remoteCaCertUri = ""
                                settings.remoteCaCertUri = ""
                            }) {
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
        }
    }

    OutlinedTextField(
        value = bridgeTopicFilter, onValueChange = { bridgeTopicFilter = it },
        label = { Text("Bridge Topic Filter") }, placeholder = { Text("# (forward all)") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.FilterAlt, null) },
        supportingText = { Text("Which local topics to forward upstream") }
    )
}

@Composable
private fun BeaconDiscoveryPanel(settings: SettingsManager, onSaved: () -> Unit) {
    var scanInterval by remember { mutableStateOf(settings.scanIntervalMs.toString()) }
    var scanDuration by remember { mutableStateOf(settings.scanDurationMs.toString()) }
    var scanPowerMode by remember { mutableStateOf(settings.scanPowerMode) }

    // Auto-save text fields on panel exit
    DisposableEffect(Unit) {
        onDispose {
            settings.scanIntervalMs = scanInterval.toLongOrNull() ?: 2500L
            settings.scanDurationMs = scanDuration.toLongOrNull() ?: 2000L
            onSaved()
        }
    }

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

    Spacer(Modifier.height(4.dp))
    Text("Scan Power Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf("LOW_POWER" to "Low Power", "BALANCED" to "Balanced", "LOW_LATENCY" to "Low Latency")
            .forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = scanPowerMode == mode,
                    onClick = {
                        scanPowerMode = mode
                        settings.scanPowerMode = mode // ← auto-save
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
    }
}

@Composable
private fun IdentityPayloadPanel(settings: SettingsManager, onSaved: () -> Unit) {
    val context = LocalContext.current
    var scannerMacLabel by remember { mutableStateOf(settings.scannerMacLabel) }
    var payloadTemplate by remember { mutableStateOf(settings.mqttPayloadTemplate) }

    // Auto-save on panel exit
    DisposableEffect(Unit) {
        onDispose {
            settings.scannerMacLabel = scannerMacLabel
            settings.mqttPayloadTemplate = payloadTemplate
            onSaved()
        }
    }

    OutlinedTextField(
        value = scannerMacLabel, onValueChange = { scannerMacLabel = it },
        label = { Text("Scanner MAC Label") },
        placeholder = { Text("Leave empty to use Device ID") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Fingerprint, null) },
        supportingText = { Text("Current ID: ${AppConfig.getDeviceId(context)}") }
    )

    SectionDivider("MQTT Payload Template")

    OutlinedTextField(
        value = payloadTemplate, onValueChange = { payloadTemplate = it },
        label = { Text("JSON Template") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        maxLines = 15,
        supportingText = {
            Text("Available: \${SCANNER_MAC}, \${BEACON_MAC}, \${RSSI}, \${TX_POWER}, \${TIMESTAMP_UTC}, \${BEACON_TYPE}, \${IBEACON_UUID}, \${IBEACON_MAJOR}, \${IBEACON_MINOR}, \${NAME}")
        }
    )
    TextButton(onClick = { payloadTemplate = SettingsManager.DEFAULT_PAYLOAD_TEMPLATE }) {
        Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Reset to Default")
    }
}

@Composable
private fun BatteryPanel() {
    val context = LocalContext.current
    val serviceStatus by ScanRepository.serviceStatus.collectAsState()
    val battery = remember(serviceStatus.batteryLevel, serviceStatus.isCharging) {
        BatteryMonitor.getStats(context)
    }
    val drainRate = remember(serviceStatus.batteryLevel) {
        BatteryMonitor.getDrainRatePerHour()
    }
    // Pastel colors for Pixel feel
    val batteryColor = when {
        battery.level > 50 -> MaterialTheme.colorScheme.primaryContainer
        battery.level > 20 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val onBatteryColor = when {
        battery.level > 50 -> MaterialTheme.colorScheme.onPrimaryContainer
        battery.level > 20 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    // Pixel-style header
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${battery.level}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                fontWeight = FontWeight.Normal
            )
            Text(
                "%",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp, start = 2.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Thick progress bar (Pixel style)
        LinearProgressIndicator(
            progress = { battery.level / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = batteryColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // Status text
        val statusText = if (battery.isCharging) "Charging • ${battery.plugType}" else "Discharging"
        Text(
            statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(24.dp))

    // Pixel-style list of cards
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BatteryListCard(
            "Status",
            if (battery.isCharging) "Charging ⚡" else "Discharging",
            isTop = true
        )
        BatteryListCard("Plug", battery.plugType.ifEmpty { "None" })
        BatteryListCard("Voltage", "${battery.voltage} V")
        BatteryListCard("Temperature", "${battery.temperature}°C")
        BatteryListCard("Health", battery.health)
        BatteryListCard(
            "Drain Rate",
            if (battery.isCharging || drainRate <= 0f) "—" else "%.1f%% / hr".format(drainRate),
            isBottom = true
        )
    }
}

@Composable
private fun BatteryListCard(title: String, subtitle: String, isTop: Boolean = false, isBottom: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = if (isTop) 24.dp else 4.dp,
            topEnd = if (isTop) 24.dp else 4.dp,
            bottomStart = if (isBottom) 24.dp else 4.dp,
            bottomEnd = if (isBottom) 24.dp else 4.dp
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServiceHealthPanel() {
    val serviceStatus by ScanRepository.serviceStatus.collectAsState()
    val runtime = Runtime.getRuntime()
    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val totalMb = runtime.maxMemory() / (1024 * 1024)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            InfoRowWithIcon(Icons.Default.Timer, "Uptime",
                ServiceHealth.formatUptime(System.currentTimeMillis() - serviceStatus.startTime))
            InfoRowWithIcon(Icons.Default.Radar, "BLE Scanner",
                if (serviceStatus.isScanning) "Running" else "Stopped",
                if (serviceStatus.isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            InfoRowWithIcon(Icons.Default.CloudQueue, "MQTT",
                if (serviceStatus.isMqttConnected) "Connected" else "Disconnected",
                if (serviceStatus.isMqttConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            InfoRow("Scan Cycles", "${serviceStatus.scanCycleCount}")
            InfoRow("Total Beacons Seen", "${serviceStatus.totalBeaconsScanned}")
            InfoRow("Messages Published", "${serviceStatus.messagesPublished}")
            InfoRow("Messages Failed", "${serviceStatus.messagesFailed}")

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            InfoRowWithIcon(Icons.Default.Memory, "Memory",
                "$usedMb MB / $totalMb MB",
                when {
                    usedMb.toFloat() / totalMb < 0.6f -> MaterialTheme.colorScheme.primary
                    usedMb.toFloat() / totalMb < 0.85f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                })
        }
    }
}

@Composable
private fun DeviceInfoPanel() {
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            InfoRow("Device ID", AppConfig.getDeviceId(context))
            InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
            InfoRow("App Version", "3.0.0")
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(
        "engineered in silence by akshat",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun NotificationsPanel() {
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.NotificationsActive, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("App Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Manage alerts, vibrations, and quiet modes in Android Settings.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Button(
                onClick = {
                    val intent = Intent().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else {
                            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text("Open Notification Settings")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LogsPanel(settings: SettingsManager) {
    var logsShown by remember { mutableStateOf(settings.logsVisible) }
    ToggleCard(
        icon = Icons.Default.Terminal,
        title = "Show Logs in Menu",
        subtitle = "Display Logs option in the navigation drawer",
        checked = logsShown,
        onCheckedChange = {
            logsShown = it
            settings.logsVisible = it // ← auto-save
        }
    )
}

// ── Shared Components ──────────────────────────────────────────

@Composable
private fun SectionDivider(title: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ToggleCard(
    icon: ImageVector, title: String, subtitle: String,
    checked: Boolean, enabled: Boolean = true,
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
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun InfoRowWithIcon(
    icon: ImageVector, label: String, value: String,
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
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun qosLabel(qos: Int): String = when (qos) {
    0 -> "Fire & forget"; 1 -> "At least once"; 2 -> "Exactly once"; else -> "Unknown"
}
