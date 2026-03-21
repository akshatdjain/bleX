package com.blegod.app.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (isTablet) {
            // ── TABLET: Split-view ──────────────────────────────
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(290.dp),
                    color = Color.Transparent
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
                modifier = Modifier.fillMaxSize()
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
        
        // Snackbar positioning
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
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
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        var searchQuery by remember { mutableStateOf("") }

        // "Search settings" functional bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search settings", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
        }

        // Define groups
        val groups = listOf(
            listOf(SettingsCategory.BATTERY, SettingsCategory.APPEARANCE, SettingsCategory.SERVICE_HEALTH, SettingsCategory.NOTIFICATIONS, SettingsCategory.DEVICE_INFO),
            listOf(SettingsCategory.PUBLISHING, SettingsCategory.LOCAL_BROKER, SettingsCategory.BEACON_DISCOVERY),
            listOf(SettingsCategory.REMOTE_SERVER, SettingsCategory.IDENTITY_PAYLOAD, SettingsCategory.LOGS)
        )

        val filteredGroups = groups.map { group ->
            group.filter { category ->
                category.label.contains(searchQuery, ignoreCase = true) || category.subtitle.contains(searchQuery, ignoreCase = true)
            }
        }.filter { it.isNotEmpty() }

        filteredGroups.forEachIndexed { groupIndex, group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { category ->
                    val isSelected = category == selected
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
                        onClick = { onSelect(category) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        category.icon, null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    category.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            if (groupIndex < filteredGroups.lastIndex) {
                Spacer(Modifier.height(8.dp))
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
        SettingTextFieldItem(
            value = when (themeMode) {
                "DARK" -> "Dark"; "LIGHT" -> "Light"; else -> "System Default"
            },
            onValueChange = {},
            readOnly = true,
            label = "Theme",
            isTop = true, isBottom = true,
            icon = Icons.Default.DarkMode,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
            modifier = Modifier.menuAnchor()
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

    SettingTextFieldItem(
        value = topicPrefix, onValueChange = { topicPrefix = it },
        label = "Topic Prefix",
        icon = Icons.Default.Topic,
        isTop = true, isBottom = false
    )

    var qosExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = qosExpanded, onExpandedChange = { qosExpanded = !qosExpanded }) {
        SettingTextFieldItem(
            value = "QoS $qos — ${qosLabel(qos)}", onValueChange = {},
            readOnly = true, label = "Quality of Service",
            icon = Icons.Default.VerifiedUser,
            isTop = false, isBottom = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qosExpanded) },
            modifier = Modifier.menuAnchor()
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

    if (!brokerEnabled) {
        SectionDivider("Direct MQTT Connection")

        SettingTextFieldItem(
            value = host, onValueChange = { host = it },
            label = "Broker Host",
            icon = Icons.Default.Dns,
            isTop = true, isBottom = false
        )
        SettingTextFieldItem(
            value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = "Port",
            icon = Icons.Default.Tag,
            isTop = false, isBottom = false,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        SettingToggleItem(
            icon = Icons.Default.Lock, title = "TLS / SSL",
            subtitle = "Encrypt MQTT traffic",
            checked = tlsEnabled,
            isTop = false, isBottom = true,
            onCheckedChange = {
                tlsEnabled = it
                settings.mqttTlsEnabled = it // ← auto-save
            }
        )

        SectionDivider("Authentication (Optional)")

        SettingTextFieldItem(
            value = username, onValueChange = { username = it },
            label = "Username",
            icon = Icons.Default.AccountCircle,
            isTop = true, isBottom = false
        )
        SettingTextFieldItem(
            value = password, onValueChange = { password = it },
            label = "Password",
            icon = Icons.Default.Key,
            isTop = false, isBottom = false,
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
        SettingTextFieldItem(
            value = keepAlive, onValueChange = { keepAlive = it.filter { c -> c.isDigit() } },
            label = "Keep Alive (s)",
            isTop = false, isBottom = false,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        SettingTextFieldItem(
            value = connTimeout, onValueChange = { connTimeout = it.filter { c -> c.isDigit() } },
            label = "Timeout (s)",
            isTop = false, isBottom = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
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

    SettingToggleItem(
        icon = Icons.Default.Hub, title = "Embedded Broker",
        subtitle = "Run MQTT broker on this device",
        checked = brokerEnabled,
        isTop = true, isBottom = !brokerEnabled,
        onCheckedChange = {
            brokerEnabled = it
            settings.brokerEnabled = it // ← auto-save (major change, saves immediately)
        }
    )

    AnimatedVisibility(visible = brokerEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingTextFieldItem(
                value = brokerPort, onValueChange = { brokerPort = it.filter { c -> c.isDigit() } },
                label = "Broker Port",
                icon = Icons.Default.Tag,
                isTop = false, isBottom = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            SettingTextFieldItem(
                value = brokerUsername, onValueChange = { brokerUsername = it },
                label = "Broker Username (Optional)",
                icon = Icons.Default.AccountCircle,
                isTop = false, isBottom = false
            )
            SettingTextFieldItem(
                value = brokerPassword, onValueChange = { brokerPassword = it },
                label = "Broker Password (Optional)",
                icon = Icons.Default.Key,
                isTop = false, isBottom = true,
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
    var webDashboardUrl by remember { mutableStateOf(settings.webDashboardUrl) }
    var upstreamPublishInterval by remember { mutableStateOf(settings.upstreamPublishIntervalS.toString()) }

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
            settings.webDashboardUrl = webDashboardUrl
            settings.upstreamPublishIntervalS = upstreamPublishInterval.toIntOrNull() ?: 0
            onSaved()
        }
    }

    // ── Web Dashboard Configuration ─────────────────────
    SectionDivider("Dashboard Configuration")

    SettingTextFieldItem(
        value = webDashboardUrl, onValueChange = { webDashboardUrl = it },
        label = "Web Dashboard URL",
        icon = Icons.Default.Dashboard,
        isTop = true, isBottom = true,
        supportingText = { Text("The URL loaded by the 'Web Dashboard' drawer item") }
    )

    Spacer(Modifier.height(4.dp))

    // ── API Base URL (for Configurator) ────────────────────
    SectionDivider("Scanner API Endpoint")

    SettingTextFieldItem(
        value = apiBaseUrl, onValueChange = { apiBaseUrl = it },
        label = "API Base URL",
        icon = Icons.Default.Api,
        isTop = true, isBottom = true,
        supportingText = { Text("Used by Configurator for zones, scanners, assets") }
    )

    Spacer(Modifier.height(4.dp))

    // ── MQTT Upstream ──────────────────────────────────────
    SectionDivider("MQTT Upstream")

    SettingTextFieldItem(
        value = remoteHost, onValueChange = { remoteHost = it },
        label = "MQTT Host",
        icon = Icons.Default.Language,
        isTop = true, isBottom = false
    )
    SettingTextFieldItem(
        value = remotePort, onValueChange = { remotePort = it.filter { c -> c.isDigit() } },
        label = "MQTT Port",
        icon = Icons.Default.Tag,
        isTop = false, isBottom = false,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    SettingToggleItem(
        icon = Icons.Default.Lock, title = "TLS",
        subtitle = "Encrypt traffic",
        checked = remoteTlsEnabled,
        isTop = false, isBottom = false,
        onCheckedChange = {
            remoteTlsEnabled = it
            settings.remoteTlsEnabled = it
        }
    )
    SettingToggleItem(
        icon = Icons.Default.Webhook, title = "WebSocket",
        subtitle = "Use WSS protocol",
        checked = remoteUseWebSocket,
        isTop = false, isBottom = false,
        onCheckedChange = {
            remoteUseWebSocket = it
            settings.remoteUseWebSocket = it
        }
    )

    SettingTextFieldItem(
        value = remoteUsername, onValueChange = { remoteUsername = it },
        label = "Remote Username",
        icon = Icons.Default.AccountCircle,
        isTop = false, isBottom = false
    )
    SettingTextFieldItem(
        value = remotePassword, onValueChange = { remotePassword = it },
        label = "Remote Password",
        icon = Icons.Default.Key,
        isTop = false, isBottom = true,
        visualTransformation = PasswordVisualTransformation()
    )

    // CA Certificate picker (when TLS enabled)
    AnimatedVisibility(visible = remoteTlsEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionDivider("Security Configuration")

            SettingToggleItem(
                icon = Icons.Default.Security, title = "Strict Verification",
                subtitle = if (remoteCaCertUri.isNotEmpty()) "Forced by custom CA" else "Validate certificates",
                checked = remoteTlsStrict || remoteCaCertUri.isNotEmpty(),
                enabled = remoteCaCertUri.isEmpty(),
                isTop = true, isBottom = false,
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
                shape = RoundedCornerShape(
                    topStart = 4.dp, topEnd = 4.dp,
                    bottomStart = 24.dp, bottomEnd = 24.dp
                ),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = when {
                        remoteCaCertUri.isNotEmpty() -> MaterialTheme.colorScheme.surface
                        remoteTlsStrict -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                ),
                elevation = CardDefaults.elevatedCardElevation(0.dp)
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

    SectionDivider("Flow Control")
    SettingTextFieldItem(
        value = bridgeTopicFilter, onValueChange = { bridgeTopicFilter = it },
        label = "Bridge Topic Filter",
        icon = Icons.Default.FilterAlt,
        isTop = true, isBottom = false,
        supportingText = { Text("Which local topics to forward upstream (# to forward all)") }
    )
    SettingTextFieldItem(
        value = upstreamPublishInterval, onValueChange = { upstreamPublishInterval = it.filter { c -> c.isDigit() } },
        label = "Upstream Publish Interval (seconds)",
        icon = Icons.Default.AvTimer,
        isTop = false, isBottom = true,
        supportingText = { Text("Delay before publishing to remote server (0 = instant)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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

        SettingTextFieldItem(
            value = scanInterval, onValueChange = { scanInterval = it.filter { c -> c.isDigit() } },
            label = "Scan Interval (ms)",
            icon = Icons.Default.Timer,
            isTop = true, isBottom = false,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        SettingTextFieldItem(
            value = scanDuration, onValueChange = { scanDuration = it.filter { c -> c.isDigit() } },
            label = "Scan Duration (ms)",
            icon = Icons.Default.HourglassBottom,
            isTop = false, isBottom = false,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Scan Power Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
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

    SettingTextFieldItem(
        value = scannerMacLabel, onValueChange = { scannerMacLabel = it },
        label = "Scanner MAC Label",
        icon = Icons.Default.Fingerprint,
        isTop = true, isBottom = true,
        supportingText = { Text("Current ID: ${AppConfig.getDeviceId(context)}") }
    )

    SectionDivider("MQTT Payload Template")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            TextField(
                value = payloadTemplate, onValueChange = { payloadTemplate = it },
                label = { Text("JSON Template") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 15,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                supportingText = {
                    Text("Available: \${SCANNER_MAC}, \${BEACON_MAC}, \${RSSI}, \${TX_POWER}, \${TIMESTAMP_UTC}, \${BEACON_TYPE}, \${IBEACON_UUID}, \${IBEACON_MAJOR}, \${IBEACON_MINOR}, \${NAME}")
                }
            )
        }
    }
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            InfoRow("Device ID", AppConfig.getDeviceId(context))
            InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
            InfoRow("App Version", "3.0.3")
        }
    }


}

@Composable
private fun NotificationsPanel() {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
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
    SettingToggleItem(
        icon = Icons.Default.Terminal,
        title = "Show Logs in Menu",
        subtitle = "Display Logs option in the navigation drawer",
        checked = logsShown,
        isTop = true, isBottom = true,
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
private fun SettingToggleItem(
    icon: ImageVector, title: String, subtitle: String = "",
    checked: Boolean, enabled: Boolean = true,
    isTop: Boolean = false, isBottom: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        onClick = { if (enabled) onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null, enabled = enabled)
        }
    }
}

@Composable
private fun SettingTextFieldItem(
    value: String, onValueChange: (String) -> Unit,
    label: String, icon: ImageVector? = null,
    isTop: Boolean = false, isBottom: Boolean = false,
    modifier: Modifier = Modifier,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = icon?.let { { Icon(it, null) } },
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = if (supportingText != null) 16.dp else 4.dp, top = 4.dp)
        )
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
