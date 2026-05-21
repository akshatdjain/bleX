package com.blex.app.ui.screens.configurator

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blex.app.AppConfig
import com.blex.app.data.SettingsManager
import kotlinx.coroutines.delay

@Composable
private fun HotspotSkeletonCards(alpha: Float) {
    val sv = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.alpha(alpha)) {
        // Tenant ID card skeleton
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.width(70.dp).height(12.dp), RoundedCornerShape(4.dp), color = sv) {}
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(Modifier.width(100.dp).height(28.dp), RoundedCornerShape(16.dp), color = sv) {}
                    Surface(Modifier.width(150.dp).height(12.dp), RoundedCornerShape(4.dp), color = sv) {}
                }
            }
        }
        // Mode info card skeleton
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(22.dp), RoundedCornerShape(4.dp), color = sv) {}
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(Modifier.width(130.dp).height(14.dp), RoundedCornerShape(4.dp), color = sv) {}
                    Surface(Modifier.width(200.dp).height(11.dp), RoundedCornerShape(4.dp), color = sv) {}
                    Surface(Modifier.width(160.dp).height(11.dp), RoundedCornerShape(4.dp), color = sv) {}
                }
            }
        }
        // Setup Credentials card skeleton
        Surface(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {}
        // Action button skeleton
        Surface(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(8.dp), color = sv) {}
        // Payload preview card skeleton
        Surface(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {}
    }
}

/** Read the tablet's local WiFi IP address */
private fun getLocalIp(context: Context): String {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ip = wifiManager.connectionInfo.ipAddress
    return String.format(
        "%d.%d.%d.%d",
        ip and 0xff,
        ip shr 8 and 0xff,
        ip shr 16 and 0xff,
        ip shr 24 and 0xff
    )
}

@Composable
fun HotspotTab() {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    // Poll hotspot status every 1.5s
    // getWifiApState() returns: 10=DISABLED, 11=DISABLING, 12=ENABLING, 13=ENABLED
    var isHotspotActive by remember { mutableStateOf(false) }
    var expandedCredentials by remember { mutableStateOf(false) }
    var isLoadingHotspotStatus by remember { mutableStateOf(true) }

    // Tenant ID is read-only, sourced from settings
    val tenantId = settings.tenantId

    LaunchedEffect(Unit) {
        // Initial read
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
        kotlinx.coroutines.delay(220)
        isLoadingHotspotStatus = false

        // Poll every 1.5s
        while (true) {
            delay(1500)
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

    // Shimmer animation for loading state
    val infiniteTransition = rememberInfiniteTransition(label = "hotspot_skeleton_shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hotspot_skeleton_alpha"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Hotspot Status Card
            if (isLoadingHotspotStatus) {
                // Skeleton loading state
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                            .alpha(shimmerAlpha),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {}
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(12.dp),
                                    shape = RoundedCornerShape(3.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {}
                                Surface(
                                    modifier = Modifier
                                        .width(80.dp)
                                        .height(16.dp),
                                    shape = RoundedCornerShape(3.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {}
                            }
                        }
                        Surface(
                            modifier = Modifier.size(12.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {}
                    }
                }
            } else {
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
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
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
                                Text(
                                    "Hotspot Status",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    if (isHotspotActive) "Active ✓" else "Inactive",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHotspotActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Pulsing dot
                        if (isHotspotActive) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        // All remaining cards hidden during skeleton — no layout shift
        if (isLoadingHotspotStatus) {
            item { HotspotSkeletonCards(shimmerAlpha) }
            return@LazyColumn
        }

        item {
            // Tenant ID read-only chip
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Badge,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Tenant ID",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (tenantId.isNotEmpty()) {
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        tenantId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                icon = {
                                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                                }
                            )
                            Text(
                                "ble/$tenantId/scanner/...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                "Not logged in — tenant ID missing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            // Mode info card — read-only, shows current configured mode
            val currentMode = settings.scannerProvisionMode
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (currentMode == "local")
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (currentMode == "local") Icons.Default.Hub else Icons.Default.Cloud,
                        null,
                        tint = if (currentMode == "local") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (currentMode == "local") "Local mode active" else "Cloud mode active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (currentMode == "local")
                                "Pi publishes to its own broker. Tablet bridges to cloud."
                            else
                                "Pi publishes directly to DGX over TLS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Change mode in Configurator → Scanners",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        item {
            // Required Credentials Info
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedCredentials = !expandedCredentials },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Setup Credentials",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            if (expandedCredentials) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = MaterialTheme.colorScheme.outline
                        )
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
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Enable your tablet hotspot first. Scanners (Pi/ESP32) must connect to it before they can be discovered and provisioned.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            // Provision payload preview — collapsible dropdown
            val localIp = remember { getLocalIp(context) }
            val isLocal = settings.scannerProvisionMode == "local"
            val mqttHost = if (isLocal) localIp else AppConfig.REMOTE_MQTT_HOST
            val mqttPort = if (isLocal) 1883 else AppConfig.REMOTE_MQTT_PORT_TLS
            val useTls = !isLocal
            var payloadExpanded by remember { mutableStateOf(false) }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                // Header row — always visible, tap to expand/collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { payloadExpanded = !payloadExpanded }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text("Provision Payload Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Icon(
                        if (payloadExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (payloadExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Expandable content
                AnimatedVisibility(visible = payloadExpanded) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(4.dp))
                        listOf(
                            "ssid"       to "<site WiFi SSID>",
                            "psk"        to "<site WiFi password>",
                            "mqtt_host"  to mqttHost,
                            "mqtt_port"  to mqttPort.toString(),
                            "tenant_id"  to tenantId.ifEmpty { "(not set)" },
                            "use_tls"    to useTls.toString(),
                            "api_url"    to AppConfig.REMOTE_API_URL,
                            "web_url"    to AppConfig.REMOTE_WEB_URL,
                        ).forEach { (key, value) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "$key:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(90.dp)
                                )
                                Text(
                                    value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
