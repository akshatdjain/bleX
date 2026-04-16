package com.blegod.app.ui.screens.configurator

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
import com.blegod.app.data.ApiService
import com.blegod.app.BeaconData
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
                Text("Start the BLE scanner from the main tab.\nActive beacons will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
            }
        } else {
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
