package com.blex.app.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import com.blex.app.AppConfig
import com.blex.app.BatteryMonitor
import com.blex.app.ServiceHealth
import com.blex.app.data.ScanRepository
import com.blex.app.data.SettingsManager

// ── Settings Categories ───────────────────────────────────────

enum class SettingsCategory(
    val label: String,
    val icon: ImageVector,
    val subtitle: String
) {
    ACCOUNT("Account", Icons.Default.AccountCircle, "Profile & tenant identity"),
    APPEARANCE("Appearance", Icons.Default.Palette, "Theme & dashboard"),
    CONNECTION("Connection", Icons.Default.CloudUpload, "MQTT & API endpoints"),
    LOCAL_BROKER("Local Broker", Icons.Default.Hub, "Embedded MQTT broker"),
    SCANNING("Scanning", Icons.Default.Radar, "BLE scan interval & power"),
    PAYLOAD("Payload", Icons.Default.DataObject, "Scanner identity & MQTT template"),
    APP("App", Icons.Default.Settings, "Notifications & logs visibility")
}

// ── Root SettingsScreen ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSettingsSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600

    var selectedCategory by remember {
        mutableStateOf<SettingsCategory?>(
            if (isTablet) SettingsCategory.ACCOUNT else null
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isTablet) {
            // ── TABLET: Split-view ──────────────────────────────
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(290.dp),
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
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
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search settings",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
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
            listOf(SettingsCategory.ACCOUNT, SettingsCategory.APPEARANCE, SettingsCategory.APP),
            listOf(
                SettingsCategory.CONNECTION,
                SettingsCategory.LOCAL_BROKER,
                SettingsCategory.SCANNING
            ),
            listOf(SettingsCategory.PAYLOAD)
        )

        val filteredGroups = groups.map { group ->
            group.filter { category ->
                category.label.contains(searchQuery, ignoreCase = true) ||
                    category.subtitle.contains(searchQuery, ignoreCase = true)
            }
        }.filter { it.isNotEmpty() }

        filteredGroups.forEachIndexed { groupIndex, group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { category ->
                    val isSelected = category == selected
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = { onSelect(category) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        category.icon,
                                        null,
                                        tint = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    category.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
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
    // 220ms skeleton flash when navigating to any settings category
    var showPanelSkeleton by remember(category) { mutableStateOf(true) }
    LaunchedEffect(category) {
        kotlinx.coroutines.delay(220)
        showPanelSkeleton = false
    }

    if (showPanelSkeleton) {
        SkeletonSettingsPanel()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (category) {
            SettingsCategory.ACCOUNT     -> AccountPanel(settings)
            SettingsCategory.APPEARANCE  -> AppearancePanel(settings)
            SettingsCategory.CONNECTION  -> ConnectionPanel(settings, onSettingsSaved)
            SettingsCategory.LOCAL_BROKER -> LocalBrokerPanel(settings, onSettingsSaved)
            SettingsCategory.SCANNING    -> ScanningPanel(settings, onSettingsSaved)
            SettingsCategory.PAYLOAD     -> PayloadPanel(settings, onSettingsSaved)
            SettingsCategory.APP         -> AppPanel(settings)
        }

        Spacer(Modifier.height(80.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
// ── SKELETON COMPONENTS ────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SkeletonSettingsPanel() {
    val infiniteTransition = rememberInfiniteTransition(label = "settings_panel_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "settings_panel_alpha"
    )
    val sv = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section label
        Surface(Modifier.width(80.dp).height(12.dp), RoundedCornerShape(4.dp), color = sv) {}
        // Card 1 - main content card
        Surface(Modifier.fillMaxWidth().height(100.dp), RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {}
        // Section label 2
        Surface(Modifier.width(60.dp).height(12.dp), RoundedCornerShape(4.dp), color = sv) {}
        // Card 2
        Surface(Modifier.fillMaxWidth().height(70.dp), RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {}
        // Card 3
        Surface(Modifier.fillMaxWidth().height(70.dp), RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {}
        // Card 4
        Surface(Modifier.fillMaxWidth().height(70.dp), RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {}
    }
}

@Composable
fun SkeletonAccountCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "account_skeleton_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "account_skeleton_alpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar placeholder
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(52.dp)
            ) {}
            // Text placeholders
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(14.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(13.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ── DETAIL PANELS ─────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════

// ── ACCOUNT ───────────────────────────────────────────────────

@Composable
private fun AccountPanel(settings: SettingsManager) {
    val context = LocalContext.current

    // Profile card
    SectionDivider("Profile")

    // Show skeleton for minimum 220ms for polish, even if data is instant
    var showAccountSkeleton by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(220)
        showAccountSkeleton = false
    }

    if (showAccountSkeleton || settings.userName.isEmpty()) {
        SkeletonAccountCard()
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AccountCircle,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val displayName = settings.userName.ifEmpty { "—" }
                    val displayEmail = settings.userEmail.ifEmpty { "—" }
                    val displayOrg = settings.orgName.ifEmpty { "—" }
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        displayEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        displayOrg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Tenant Identity card
    SectionDivider("Tenant Identity")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Tenant ID with chip-style badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Tenant ID",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val tid = settings.tenantId.ifEmpty { "—" }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        tid,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            val topicDisplay = if (settings.tenantId.isNotEmpty())
                "${settings.mqttTopicTenant}/…"
            else
                "ble/scanner/…"
            InfoRow("MQTT Topic", topicDisplay)
            InfoRow("API", AppConfig.REMOTE_API_URL.removePrefix("https://"))
            InfoRow("Dashboard", AppConfig.REMOTE_WEB_URL.removePrefix("https://"))
        }
    }

    Spacer(Modifier.height(4.dp))

    // Log Out button
    OutlinedButton(
        onClick = {
            settings.clearAuth()
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        },
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Log Out")
    }
}

// ── APPEARANCE ────────────────────────────────────────────────

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
                "DARK" -> "Dark"
                "LIGHT" -> "Light"
                else -> "System Default"
            },
            onValueChange = {},
            readOnly = true,
            label = "Theme",
            isTop = true, isBottom = true,
            icon = Icons.Default.DarkMode,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = themeExpanded,
            onDismissRequest = { themeExpanded = false }
        ) {
            listOf(
                "SYSTEM" to "System Default",
                "DARK" to "Dark",
                "LIGHT" to "Light"
            ).forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        themeMode = mode
                        themeExpanded = false
                        settings.themeMode = mode
                    }
                )
            }
        }
    }
}

// ── CONNECTION ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionPanel(settings: SettingsManager, onSaved: () -> Unit) {
    val context = LocalContext.current

    var webDashboardUrl by remember { mutableStateOf(settings.webDashboardUrl) }
    var remoteHost by remember { mutableStateOf(settings.remoteHost) }
    var remotePort by remember { mutableStateOf(settings.remotePort.toString()) }
    var remoteTlsEnabled by remember { mutableStateOf(settings.remoteTlsEnabled) }
    var remoteTlsStrict by remember { mutableStateOf(settings.remoteTlsStrict) }
    var remoteUseWebSocket by remember { mutableStateOf(settings.remoteUseWebSocket) }
    var remoteWsPath by remember { mutableStateOf(settings.remoteWebSocketPath) }
    var remoteUsername by remember { mutableStateOf(settings.remoteUsername) }
    var remotePassword by remember { mutableStateOf(settings.remotePassword) }
    var remoteCaCertUri by remember { mutableStateOf(settings.remoteCaCertUri) }
    var bridgeTopicFilter by remember { mutableStateOf(settings.bridgeTopicFilter) }
    var upstreamPublishInterval by remember {
        mutableStateOf(settings.upstreamPublishIntervalS.toString())
    }
    var remoteClientId by remember { mutableStateOf(settings.remoteClientId) }
    var remoteKeepAlive by remember { mutableStateOf(settings.mqttKeepAlive.toString()) }
    var remoteTimeout by remember { mutableStateOf(settings.mqttConnectionTimeout.toString()) }

    var showAdvanced by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            settings.remoteHost = remoteHost
            settings.remotePort = remotePort.toIntOrNull() ?: 443
            settings.remoteWebSocketPath = remoteWsPath
            settings.remoteUsername = remoteUsername
            settings.remotePassword = remotePassword
            settings.remoteCaCertUri = remoteCaCertUri
            settings.bridgeTopicFilter = bridgeTopicFilter
            settings.upstreamPublishIntervalS = upstreamPublishInterval.toIntOrNull() ?: 0
            settings.remoteClientId = remoteClientId
            settings.mqttKeepAlive = remoteKeepAlive.toIntOrNull() ?: 30
            settings.mqttConnectionTimeout = remoteTimeout.toIntOrNull() ?: 10
            settings.webDashboardUrl = webDashboardUrl
        }
    }

    // ── Collapsed summary ─────────────────────────────────────
    SectionDivider("Cloud MQTT")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val displayHost = remoteHost.ifEmpty { AppConfig.REMOTE_MQTT_HOST }
            val displayPort = remotePort.ifEmpty { "443" }
            val protocol = when {
                remoteUseWebSocket && remoteTlsEnabled -> "WSS"
                remoteUseWebSocket -> "WS"
                remoteTlsEnabled -> "TLS"
                else -> "TCP"
            }
            val wsPath = remoteWsPath.ifEmpty { AppConfig.REMOTE_MQTT_WSS_PATH }
            val authDisplay = if (remoteUsername.isNotEmpty())
                "${remoteUsername} / ●●●●"
            else
                "None"

            InfoRow("Host", displayHost)
            InfoRow("Port", displayPort)
            InfoRow("Protocol", protocol)
            if (remoteUseWebSocket) InfoRow("Path", wsPath)
            InfoRow("Auth", authDisplay)
        }
    }

    SectionDivider("Web Dashboard")

    SettingTextFieldItem(
        value = webDashboardUrl,
        onValueChange = { webDashboardUrl = it },
        label = "Web Dashboard URL",
        icon = Icons.Default.Dashboard,
        isTop = true, isBottom = true,
        supportingText = { Text("Defaults to ${AppConfig.REMOTE_WEB_URL}") }
    )

    // Advanced toggle
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { showAdvanced = !showAdvanced }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Advanced Override",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    // ── Advanced fields (expanded) ────────────────────────────
    AnimatedVisibility(visible = showAdvanced) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            SectionDivider("MQTT Host")

            SettingTextFieldItem(
                value = remoteHost,
                onValueChange = { remoteHost = it },
                label = "Remote Host",
                icon = Icons.Default.Language,
                isTop = true, isBottom = false,
                supportingText = { Text("Default: ${AppConfig.REMOTE_MQTT_HOST}") }
            )
            SettingTextFieldItem(
                value = remotePort,
                onValueChange = { remotePort = it.filter { c -> c.isDigit() } },
                label = "Port",
                icon = Icons.Default.Tag,
                isTop = false, isBottom = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            SettingToggleItem(
                icon = Icons.Default.Lock,
                title = "TLS",
                subtitle = "Encrypt traffic",
                checked = remoteTlsEnabled,
                isTop = false, isBottom = false,
                onCheckedChange = {
                    remoteTlsEnabled = it
                    settings.remoteTlsEnabled = it
                }
            )
            SettingToggleItem(
                icon = Icons.Default.Webhook,
                title = "WebSocket",
                subtitle = "Use WSS / WS protocol",
                checked = remoteUseWebSocket,
                isTop = false, isBottom = false,
                onCheckedChange = {
                    remoteUseWebSocket = it
                    settings.remoteUseWebSocket = it
                }
            )
            AnimatedVisibility(visible = remoteUseWebSocket) {
                SettingTextFieldItem(
                    value = remoteWsPath,
                    onValueChange = { remoteWsPath = it },
                    label = "WebSocket Path",
                    icon = Icons.Default.Route,
                    isTop = false, isBottom = true,
                    supportingText = { Text("Default: ${AppConfig.REMOTE_MQTT_WSS_PATH}") }
                )
            }

            SectionDivider("Authentication")

            SettingTextFieldItem(
                value = remoteUsername,
                onValueChange = { remoteUsername = it },
                label = "Username",
                icon = Icons.Default.AccountCircle,
                isTop = true, isBottom = false
            )
            SettingTextFieldItem(
                value = remotePassword,
                onValueChange = { remotePassword = it },
                label = "Password",
                icon = Icons.Default.Key,
                isTop = false, isBottom = false,
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            "Toggle password"
                        )
                    }
                },
                visualTransformation = if (showPassword)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation()
            )
            SettingTextFieldItem(
                value = remoteClientId,
                onValueChange = { remoteClientId = it },
                label = "Client ID",
                icon = Icons.Default.Badge,
                isTop = false, isBottom = true,
                supportingText = { Text("Unique ID for this device (e.g. tablet-1)") }
            )

            SectionDivider("Timing")

            SettingTextFieldItem(
                value = remoteKeepAlive,
                onValueChange = { remoteKeepAlive = it.filter { c -> c.isDigit() } },
                label = "Keep Alive (s)",
                icon = Icons.Default.SyncAlt,
                isTop = true, isBottom = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Heartbeat interval (default: 30)") }
            )
            SettingTextFieldItem(
                value = remoteTimeout,
                onValueChange = { remoteTimeout = it.filter { c -> c.isDigit() } },
                label = "Connection Timeout (s)",
                icon = Icons.Default.Timer,
                isTop = false, isBottom = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // CA Certificate picker (when TLS enabled)
            AnimatedVisibility(visible = remoteTlsEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionDivider("Security")

                    SettingToggleItem(
                        icon = Icons.Default.Security,
                        title = "Strict Verification",
                        subtitle = if (remoteCaCertUri.isNotEmpty())
                            "Forced by custom CA"
                        else
                            "Validate certificates",
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
                                context.contentResolver.takePersistableUriPermission(
                                    it,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
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
                                            remoteCaCertUri.isNotEmpty() ->
                                                "Custom CA Certificate Active"
                                            remoteTlsStrict ->
                                                "Strict Mode (System CA)"
                                            else ->
                                                "Using Insecure 'Trust All' Mode"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        when {
                                            remoteCaCertUri.isNotEmpty() ->
                                                "Only connections verified by this CA will be allowed."
                                            remoteTlsStrict ->
                                                "Compatible with official domains with valid certificates."
                                            else ->
                                                "This ignores all SSL validity checks (vulnerable to MitM)."
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
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
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
                                    Icon(
                                        Icons.Default.UploadFile,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
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
                value = bridgeTopicFilter,
                onValueChange = { bridgeTopicFilter = it },
                label = "Bridge Topic Filter",
                icon = Icons.Default.FilterAlt,
                isTop = true, isBottom = false,
                supportingText = {
                    Text("Which local topics to forward upstream (# to forward all)")
                }
            )
            SettingTextFieldItem(
                value = upstreamPublishInterval,
                onValueChange = {
                    upstreamPublishInterval = it.filter { c -> c.isDigit() }
                },
                label = "Upstream Publish Interval (s)",
                icon = Icons.Default.AvTimer,
                isTop = false, isBottom = true,
                supportingText = {
                    Text("Delay before publishing to remote server (0 = instant)")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

// ── LOCAL BROKER ──────────────────────────────────────────────

@Composable
private fun LocalBrokerPanel(settings: SettingsManager, onSaved: () -> Unit) {
    var brokerEnabled by remember { mutableStateOf(settings.brokerEnabled) }
    var brokerPort by remember { mutableStateOf(settings.brokerPort.toString()) }
    var brokerUsername by remember { mutableStateOf(settings.brokerUsername) }
    var brokerPassword by remember { mutableStateOf(settings.brokerPassword) }

    DisposableEffect(Unit) {
        onDispose {
            settings.brokerPort = brokerPort.toIntOrNull() ?: 1883
            settings.brokerUsername = brokerUsername
            settings.brokerPassword = brokerPassword
        }
    }

    SettingToggleItem(
        icon = Icons.Default.Hub,
        title = "Embedded Broker",
        subtitle = "Run MQTT broker on this device",
        checked = brokerEnabled,
        isTop = true, isBottom = !brokerEnabled,
        onCheckedChange = {
            brokerEnabled = it
            settings.brokerEnabled = it
        }
    )

    AnimatedVisibility(visible = brokerEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingTextFieldItem(
                value = brokerPort,
                onValueChange = { brokerPort = it.filter { c -> c.isDigit() } },
                label = "Broker Port",
                icon = Icons.Default.Tag,
                isTop = false, isBottom = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            SettingTextFieldItem(
                value = brokerUsername,
                onValueChange = { brokerUsername = it },
                label = "Broker Username (Optional)",
                icon = Icons.Default.AccountCircle,
                isTop = false, isBottom = false
            )
            SettingTextFieldItem(
                value = brokerPassword,
                onValueChange = { brokerPassword = it },
                label = "Broker Password (Optional)",
                icon = Icons.Default.Key,
                isTop = false, isBottom = true,
                visualTransformation = PasswordVisualTransformation()
            )
        }
    }
}

// ── SCANNING (formerly BeaconDiscovery) ───────────────────────

@Composable
private fun ScanningPanel(settings: SettingsManager, onSaved: () -> Unit) {
    var scanInterval by remember { mutableStateOf(settings.scanIntervalMs.toString()) }
    var scanDuration by remember { mutableStateOf(settings.scanDurationMs.toString()) }
    var scanPowerMode by remember { mutableStateOf(settings.scanPowerMode) }

    DisposableEffect(Unit) {
        onDispose {
            settings.scanIntervalMs = scanInterval.toLongOrNull() ?: 2500L
            settings.scanDurationMs = scanDuration.toLongOrNull() ?: 2000L
        }
    }

    SettingTextFieldItem(
        value = scanInterval,
        onValueChange = { scanInterval = it.filter { c -> c.isDigit() } },
        label = "Scan Interval (ms)",
        icon = Icons.Default.Timer,
        isTop = true, isBottom = false,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    SettingTextFieldItem(
        value = scanDuration,
        onValueChange = { scanDuration = it.filter { c -> c.isDigit() } },
        label = "Scan Duration (ms)",
        icon = Icons.Default.HourglassBottom,
        isTop = false, isBottom = false,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 4.dp, topEnd = 4.dp,
            bottomStart = 24.dp, bottomEnd = 24.dp
        ),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Scan Power Mode",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "LOW_POWER" to "Low Power",
                    "BALANCED" to "Balanced",
                    "LOW_LATENCY" to "Low Latency"
                ).forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = scanPowerMode == mode,
                        onClick = {
                            scanPowerMode = mode
                            settings.scanPowerMode = mode
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

// ── PAYLOAD (formerly IdentityPayload) ────────────────────────

@Composable
private fun PayloadPanel(settings: SettingsManager, onSaved: () -> Unit) {
    val context = LocalContext.current
    var scannerMacLabel by remember { mutableStateOf(settings.scannerMacLabel) }
    var payloadTemplate by remember { mutableStateOf(settings.mqttPayloadTemplate) }

    DisposableEffect(Unit) {
        onDispose {
            settings.scannerMacLabel = scannerMacLabel
            settings.mqttPayloadTemplate = payloadTemplate
        }
    }

    SettingTextFieldItem(
        value = scannerMacLabel,
        onValueChange = { scannerMacLabel = it },
        label = "Scanner MAC Label",
        icon = Icons.Default.Fingerprint,
        isTop = true, isBottom = true,
        supportingText = { Text("Current ID: ${AppConfig.getDeviceId(context)}") }
    )

    SectionDivider("MQTT Payload Template")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            TextField(
                value = payloadTemplate,
                onValueChange = { payloadTemplate = it },
                label = { Text("JSON Template") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
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
                    Text(
                        "Available: \${SCANNER_MAC}, \${BEACON_MAC}, \${RSSI}, \${TX_POWER}, " +
                            "\${TIMESTAMP_UTC}, \${BEACON_TYPE}, \${IBEACON_UUID}, " +
                            "\${IBEACON_MAJOR}, \${IBEACON_MINOR}, \${NAME}"
                    )
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

// ── PUBLISHING (trimmed — topic prefix + QoS only) ────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishingPanel(settings: SettingsManager, onSaved: () -> Unit) {
    var topicPrefix by remember { mutableStateOf(settings.mqttTopicPrefix) }
    var qos by remember { mutableIntStateOf(settings.mqttQos) }

    DisposableEffect(Unit) {
        onDispose {
            settings.mqttTopicPrefix = topicPrefix
            onSaved()
        }
    }

    SettingTextFieldItem(
        value = topicPrefix,
        onValueChange = { topicPrefix = it },
        label = "Topic Prefix",
        icon = Icons.Default.Topic,
        isTop = true, isBottom = false
    )

    var qosExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = qosExpanded,
        onExpandedChange = { qosExpanded = !qosExpanded }
    ) {
        SettingTextFieldItem(
            value = "QoS $qos — ${qosLabel(qos)}",
            onValueChange = {},
            readOnly = true,
            label = "Quality of Service",
            icon = Icons.Default.VerifiedUser,
            isTop = false, isBottom = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qosExpanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = qosExpanded,
            onDismissRequest = { qosExpanded = false }
        ) {
            listOf(0, 1, 2).forEach { level ->
                DropdownMenuItem(
                    text = { Text("QoS $level — ${qosLabel(level)}") },
                    onClick = {
                        qos = level
                        qosExpanded = false
                        settings.mqttQos = level
                    }
                )
            }
        }
    }
}

// ── APP (merged Notifications + Logs) ────────────────────────

@Composable
private fun AppPanel(settings: SettingsManager) {
    val context = LocalContext.current

    // Visibility section
    SectionDivider("Visibility")

    var logsShown by remember { mutableStateOf(settings.logsVisible) }
    SettingToggleItem(
        icon = Icons.Default.Terminal,
        title = "Show Logs in Menu",
        subtitle = "Display Logs in nav drawer",
        checked = logsShown,
        isTop = true, isBottom = true,
        onCheckedChange = {
            logsShown = it
            settings.logsVisible = it
        }
    )

    // System section
    SectionDivider("System")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
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
                        Icons.Default.NotificationsActive,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "App Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Manage alerts, vibrations, and quiet modes in Android Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
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

// ═══════════════════════════════════════════════════════════════
// ── DIAGNOSTICS PANELS (kept for LogScreen / DiagnosticsScreen)
// ═══════════════════════════════════════════════════════════════

@Composable
fun BatteryPanel() {
    val context = LocalContext.current
    val serviceStatus by ScanRepository.serviceStatus.collectAsState()
    val battery = remember(serviceStatus.batteryLevel, serviceStatus.isCharging) {
        BatteryMonitor.getStats(context)
    }
    val drainRate = remember(serviceStatus.batteryLevel) {
        BatteryMonitor.getDrainRatePerHour()
    }
    val batteryColor = when {
        battery.level > 50 -> MaterialTheme.colorScheme.primaryContainer
        battery.level > 20 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }

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

        val statusText = if (battery.isCharging) "Charging • ${battery.plugType}" else "Discharging"
        Text(
            statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(24.dp))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BatteryListCard("Status", if (battery.isCharging) "Charging ⚡" else "Discharging", isTop = true)
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
fun BatteryListCard(
    title: String,
    subtitle: String,
    isTop: Boolean = false,
    isBottom: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
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
fun ServiceHealthPanel() {
    val serviceStatus by ScanRepository.serviceStatus.collectAsState()
    val runtime = Runtime.getRuntime()
    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val totalMb = runtime.maxMemory() / (1024 * 1024)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            InfoRowWithIcon(
                Icons.Default.Timer, "Uptime",
                ServiceHealth.formatUptime(System.currentTimeMillis() - serviceStatus.startTime)
            )
            InfoRowWithIcon(
                Icons.Default.Radar, "BLE Scanner",
                if (serviceStatus.isScanning) "Running" else "Stopped",
                if (serviceStatus.isScanning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            InfoRowWithIcon(
                Icons.Default.CloudQueue, "MQTT",
                if (serviceStatus.isMqttConnected) "Connected" else "Disconnected",
                if (serviceStatus.isMqttConnected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            InfoRow("Scan Cycles", "${serviceStatus.scanCycleCount}")
            InfoRow("Total Beacons Seen", "${serviceStatus.totalBeaconsScanned}")
            InfoRow("Messages Published", "${serviceStatus.messagesPublished}")
            InfoRow("Messages Failed", "${serviceStatus.messagesFailed}")

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
}

@Composable
fun DeviceInfoPanel() {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            InfoRow("Device ID", AppConfig.getDeviceId(context))
            InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
            InfoRow("App Version", "3.0.6")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ── SHARED COMPONENTS ─────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════

@Composable
fun SectionDivider(title: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    isTop: Boolean = false,
    isBottom: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = { if (enabled) onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled
            )
        }
    }
}

@Composable
fun SettingTextFieldItem(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector? = null,
    isTop: Boolean = false,
    isBottom: Boolean = false,
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
        color = MaterialTheme.colorScheme.surfaceContainer
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    bottom = if (supportingText != null) 16.dp else 4.dp,
                    top = 4.dp
                )
        )
    }
}

@Composable
fun InfoRowWithIcon(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

fun qosLabel(qos: Int): String = when (qos) {
    0 -> "Fire & forget"
    1 -> "At least once"
    2 -> "Exactly once"
    else -> "Unknown"
}
