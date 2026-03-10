package com.blegod.app.ui.screens.configurator

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
