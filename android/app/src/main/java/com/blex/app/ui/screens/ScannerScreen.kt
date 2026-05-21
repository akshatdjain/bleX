package com.blex.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings
import com.blex.app.BeaconData
import com.blex.app.data.ApiService
import com.blex.app.data.ScanRepository
import com.blex.app.data.ServiceStatus
import com.blex.app.data.SettingsManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private enum class SortMode(val label: String) {
    RSSI("RSSI"),
    NAME("Name"),
    TYPE("Type"),
    LAST_SEEN("Last Seen")
}

private enum class FilterMode(val label: String) {
    ALL("All"),
    IBEACON("iBeacon"),
    EDDYSTONE("Eddystone")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen() {
    // FIX 1: Collect status separately from beacons.
    // ServiceStatus is now @Immutable, so StatusChipRow only recomposes when
    // the status object changes (BLE on/off, MQTT connect/disconnect) — not
    // on every 500ms beacon RSSI update.
    // Ref: https://developer.android.com/develop/ui/compose/performance/stability
    val status by ScanRepository.serviceStatus.collectAsState()
    val registeredAssets by ScanRepository.registeredAssets.collectAsState()
    val assetsLoaded by ScanRepository.assetsLoaded.collectAsState()
    val context = LocalContext.current
    val settingsMgr = remember { SettingsManager.getInstance(context) }
    val provisionMode = remember { settingsMgr.scannerProvisionMode }
    val remoteHost = remember { settingsMgr.remoteHost }
    var selectedBeacon by remember { mutableStateOf<BeaconData?>(null) }

    // Skeleton loading state: true for first 3 seconds while beacons are empty and BLE is scanning
    var isInitializing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3000)
        isInitializing = false
    }

    // FIX 2: currentTime ticker is REMOVED from ScannerScreen.
    //
    // The old code had `currentTime` collected here and passed as a Long parameter
    // to BeaconCard. Because Long is a primitive and BeaconData is @Immutable,
    // Compose can't skip BeaconCard when currentTime changes — it recomposes EVERY
    // card every second even if no beacon data changed. With 20 beacons that's
    // 20 full card recompositions per second just for "2s ago" → "3s ago" text.
    //
    // The fix: TimeSinceText() is a tiny isolated composable that owns its own
    // currentTime state. Only that single Text recomposes each second.
    // The rest of BeaconCard is completely unaffected by time ticks.
    // Ref: https://developer.android.com/develop/ui/compose/performance/bestpractices#defer-reads

    // Sort & Filter state
    var sortMode by remember { mutableStateOf(SortMode.RSSI) }
    var filterMode by remember { mutableStateOf(FilterMode.ALL) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }

    // FIX 3: derivedStateOf — read the State<T> object produced by collectAsState()
    // INSIDE the derivedStateOf lambda so Compose can track it as a live state read.
    //
    // The old pattern `val beacons by flow.collectAsState()` then referencing
    // `beacons` inside `derivedStateOf` captures a snapshot of the list at the
    // time the remember block first runs. The `by` delegation unwraps the State<T>
    // to a plain List<BeaconData> value — so derivedStateOf never sees state reads
    // inside its lambda and doesn't know to re-run when beacons change, UNLESS
    // sortMode or filterMode also change (which are the remember() keys).
    //
    // By holding the State<T> object (not delegating it with `by`) and reading
    // `.value` inside the derivedStateOf lambda, Compose correctly tracks the
    // beacons state as a dependency of the derived state.
    // Ref: https://developer.android.com/develop/ui/compose/performance/bestpractices#use-derivedstateof
    val beaconsState = ScanRepository.beacons.collectAsState()
    val sortedBeacons by remember(sortMode, filterMode) {
        derivedStateOf {
            val rawBeacons = beaconsState.value   // live State<T> read — tracked by derivedStateOf
            val filtered = when (filterMode) {
                FilterMode.ALL -> rawBeacons
                FilterMode.IBEACON -> rawBeacons.filter { it.beaconType == "iBeacon" }
                FilterMode.EDDYSTONE -> rawBeacons.filter { it.beaconType?.startsWith("Eddystone") == true }
            }
            when (sortMode) {
                SortMode.RSSI -> filtered.sortedByDescending { it.rssi }
                SortMode.NAME -> filtered.sortedBy { it.name ?: "zzz" }
                SortMode.TYPE -> filtered.sortedBy { it.beaconType ?: "zzz" }
                SortMode.LAST_SEEN -> filtered.sortedByDescending { it.timestamp }
            }
        }
    }

    val scope = rememberCoroutineScope()

    // Pull-to-refresh: show full skeleton + re-fetch asset names
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            isInitializing = true              // show beacon list skeleton
            ScanRepository.resetAssetsLoaded() // show name skeletons on each card
            delay(220)                         // let skeletons render for polish
            try {
                ApiService.configuredBaseUrl = settingsMgr.apiBaseUrl
                ApiService.tenantId = settingsMgr.tenantId
                val assets = ApiService.getAssets()
                ScanRepository.setRegisteredAssets(assets)
            } catch (_: Exception) {
                ScanRepository.setRegisteredAssets(emptyList())
            }
            isInitializing = false
            isRefreshing = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // BLE status + sort row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Device count
            Text(
                "${sortedBeacons.size} devices",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Sort button
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.Default.Sort, "Sort")
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (sortMode == mode) {
                                            Icon(Icons.Default.Check, null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(mode.label)
                                    }
                                },
                                onClick = { sortMode = mode; sortMenuExpanded = false }
                            )
                        }
                    }
                }

                // Status chips — mode-aware
                // StatusChipRow only recomposes when status changes because
                // ServiceStatus is @Immutable.
                StatusChipRow(
                    status = status,
                    provisionMode = provisionMode,
                    remoteHost = remoteHost
                )
            }
        }

        // Location Services OFF banner — critical for Samsung/OnePlus
        if (!status.isLocationEnabled) {
            val ctx = LocalContext.current
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Location Services OFF",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "BLE scanning requires Location to be enabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Enable", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Filter chips
        FilterChipRow(filterMode) { filterMode = it }

        // Beacon list with pull-to-refresh
        if (isInitializing) {
            // Show skeleton — initial load OR pull-to-refresh triggered
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(4) {
                    SkeletonBeaconCard()
                }
            }
        } else if (sortedBeacons.isEmpty()) {
            EmptyState(status.isScanning)
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // FIX 4: Add contentType so Compose can pool item node trees.
                    // All items share the same structure so they get the same type.
                    // key = { it.mac } was already correct — keeps item identity stable
                    // across list reorders so only actually moved items animate.
                    // Ref: https://developer.android.com/develop/ui/compose/performance/bestpractices#use-lazylist-key
                    items(
                        items = sortedBeacons,
                        key = { it.mac },
                        contentType = { "beacon_card" }
                    ) { beacon ->
                        // FIX 5: registeredName lookup is cheap (HashMap.get), so doing
                        // it here is fine. But we memoize the displayName string with
                        // remember(beacon.mac) so it only recomputes when the MAC changes.
                        // Derive name from registered assets — shows skeleton until assetsLoaded=true
                        val assetName = registeredAssets[beacon.mac.uppercase()]?.assetName
                        val displayName = assetName ?: beacon.name ?: "Unknown Device"
                        val isRegistered = assetName != null
                        val showNameSkeleton = !assetsLoaded && assetName == null
                        // FIX 6: Lambda stabilised with remember(beacon.mac).
                        // Using beacon.mac (a String) as the key rather than beacon
                        // (the full object) means the lambda is NOT recreated when
                        // RSSI updates cause a new BeaconData instance for the same MAC.
                        // BeaconData is @Immutable so equals() works by field value, but
                        // remember() uses referential equality by default for objects.
                        // Using the stable String key avoids needless lambda allocation
                        // on every scan cycle.
                        // Ref: https://developer.android.com/develop/ui/compose/performance/bestpractices
                        val onCardClick = remember(beacon.mac) { { selectedBeacon = beacon } }
                        BeaconCard(
                            beacon = beacon,
                            displayName = displayName,
                            isRegistered = isRegistered,
                            showNameSkeleton = showNameSkeleton,
                            onClick = onCardClick
                        )
                    }
                }
            }
        }
    }

    // Bottom sheet for beacon details
    selectedBeacon?.let { beacon ->
        BeaconDetailSheet(
            beacon = beacon,
            onDismiss = { selectedBeacon = null }
        )
    }
}

// ── Status chip row ────────────────────────────────────────────

@Composable
private fun StatusChipRow(
    status: ServiceStatus,
    provisionMode: String,
    remoteHost: String
) {
    val isLocal = provisionMode == "local"
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // BLE chip — always shown
        MiniChip(
            label = "BLE",
            active = status.isScanning,
            activeIcon = Icons.Default.BluetoothSearching,
            inactiveIcon = Icons.Default.BluetoothDisabled
        )

        if (isLocal) {
            // Broker chip
            MiniChip(
                label = "Broker",
                active = status.isBrokerRunning,
                activeIcon = Icons.Default.Router,
                inactiveIcon = Icons.Default.Router
            )
            // Pi/local bridge chip — only if remote is configured
            if (remoteHost.isNotEmpty()) {
                MiniChip(
                    label = "Pi",
                    active = status.isBridgeConnected,
                    activeIcon = Icons.Default.Hub,
                    inactiveIcon = Icons.Default.Hub
                )
            }
        } else {
            // Cloud bridge chip — only if remote is configured
            if (remoteHost.isNotEmpty()) {
                MiniChip(
                    label = "Cloud",
                    active = status.isBridgeConnected,
                    activeIcon = Icons.Default.CloudDone,
                    inactiveIcon = Icons.Default.CloudOff
                )
            }
        }
    }
}

@Composable
private fun MiniChip(
    label: String,
    active: Boolean,
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (active) activeIcon else inactiveIcon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (active)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (active)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun FilterChipRow(selected: FilterMode, onSelect: (FilterMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(mode.label) },
                leadingIcon = if (selected == mode) {
                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun StatusChip(status: ServiceStatus) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (status.isScanning)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (status.isScanning)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
            )
            Text(
                if (status.isScanning) "Live" else "Stopped",
                style = MaterialTheme.typography.labelSmall,
                color = if (status.isScanning)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun StatsBar(status: ServiceStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(icon = Icons.Default.Loop, value = "${status.scanCycleCount}", label = "Cycles")
            StatItem(icon = Icons.Default.Bluetooth, value = "${status.totalBeaconsScanned}", label = "Scanned")
            StatItem(icon = Icons.Default.CloudUpload, value = "${status.messagesPublished}", label = "Published")
            StatItem(
                icon = if (status.isMqttConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                value = if (status.isMqttConnected) "OK" else "Down",
                label = "MQTT"
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(isScanning: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (isScanning) "Scanning for beacons…" else "Scanner not running",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isScanning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.width(200.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// FIX 7: BeaconCard no longer accepts currentTime: Long.
//
// Previously every BeaconCard recomposed every second because currentTime
// was passed as a parameter from the parent. Now the card is fully stable:
// it only recomposes when its beacon data (RSSI, name, etc.) changes.
// The "last seen" text is isolated in TimeSinceText() which manages its own
// 1-second ticker internally.
// Ref: https://developer.android.com/develop/ui/compose/performance/bestpractices#defer-reads
@Composable
private fun BeaconCard(
    beacon: BeaconData,
    displayName: String,
    isRegistered: Boolean,
    showNameSkeleton: Boolean = false,
    onClick: () -> Unit
) {
    val signalStrength = getSignalStrength(beacon.rssi)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal icon
            Surface(
                shape = CircleShape,
                color = signalStrength.color(),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        signalStrength.icon(),
                        contentDescription = "Signal",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showNameSkeleton) {
                        // Skeleton name line while registered assets are loading
                        val inf = rememberInfiniteTransition(label = "name_shimmer")
                        val a by inf.animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "na")
                        Surface(
                            modifier = Modifier.width(100.dp).height(14.dp).alpha(a),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {}
                    } else {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    // Beacon type badge
                    beacon.beaconType?.let { type ->
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (type == "iBeacon")
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                type,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (type == "iBeacon")
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Text(
                    beacon.mac,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // FIX 8: TimeSinceText is a dedicated composable that owns its own
                // 1-second ticker. Only this tiny Text recomposes each second.
                // The outer BeaconCard (and all its layout/draw work) is completely
                // unaffected by time ticks when the beacon data hasn't changed.
                TimeSinceText(timestamp = beacon.timestamp)
            }

            // RSSI
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${beacon.rssi}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = signalStrength.color()
                )
                Text(
                    "dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Isolated composable that updates every second to show relative time.
 *
 * By owning the ticker here instead of in ScannerScreen or BeaconCard, we
 * guarantee that only this Text node enters recomposition each second.
 * No other part of the card — the ElevatedCard, signal icon, name row,
 * MAC address, or RSSI column — touches this state and therefore none of
 * them recompose on timer ticks.
 *
 * Ref: https://developer.android.com/develop/ui/compose/performance/bestpractices#defer-reads
 */
@Composable
private fun TimeSinceText(timestamp: Long) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }
    Text(
        getTimeSince(timestamp, currentTime),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeaconDetailSheet(
    beacon: BeaconData,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    beacon.name ?: "Unknown Device",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                beacon.beaconType?.let { type ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (type == "iBeacon")
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            type,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            color = if (type == "iBeacon")
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                beacon.mac,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Standard details
            DetailRow("RSSI", "${beacon.rssi} dBm")
            DetailRow("TX Power", beacon.txPower?.let { "$it dBm" } ?: "N/A")
            DetailRow("Device ID", beacon.deviceId)
            DetailRow("Last Seen", formatTimestamp(beacon.timestamp))
            DetailRow("Signal", getSignalStrength(beacon.rssi).label)

            // iBeacon-specific
            if (beacon.beaconType == "iBeacon") {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    "iBeacon Data",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                DetailRow("UUID", beacon.ibeaconUuid ?: "N/A")
                DetailRow("Major", beacon.ibeaconMajor?.toString() ?: "N/A")
                DetailRow("Minor", beacon.ibeaconMinor?.toString() ?: "N/A")
            }

            // Eddystone-specific
            if (beacon.beaconType?.startsWith("Eddystone") == true) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Eddystone Data",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                DetailRow("Frame Type", beacon.beaconType ?: "N/A")
                beacon.eddystoneNamespace?.let { DetailRow("Namespace", it) }
                beacon.eddystoneInstance?.let { DetailRow("Instance", it) }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
            fontWeight = FontWeight.Medium,
            fontFamily = if (label == "UUID" || label == "Namespace" || label == "Instance" ||
                label == "Device ID" || label == "Last Seen")
                FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

// ── Skeleton Beacon Card ──────────────────────────────────────────

@Composable
private fun SkeletonBeaconCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "beacon_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beacon_shimmer_alpha"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal icon placeholder (44dp circle)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {}

            Spacer(Modifier.width(14.dp))

            // Info placeholders
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(12.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(11.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
            }

            // RSSI placeholder on right
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    modifier = Modifier
                        .width(50.dp)
                        .height(16.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .width(35.dp)
                        .height(12.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────

private enum class SignalStrength(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    WEAK("Weak");

    @Composable
    fun color() = when (this) {
        EXCELLENT -> MaterialTheme.colorScheme.primary
        GOOD -> MaterialTheme.colorScheme.tertiary
        FAIR -> MaterialTheme.colorScheme.secondary
        WEAK -> MaterialTheme.colorScheme.error
    }

    fun icon() = when (this) {
        EXCELLENT -> Icons.Default.SignalCellular4Bar
        GOOD -> Icons.Default.NetworkCell
        FAIR -> Icons.Default.SignalCellularAlt2Bar
        WEAK -> Icons.Default.SignalCellularAlt1Bar
    }
}

private fun getSignalStrength(rssi: Int): SignalStrength = when {
    rssi >= -50 -> SignalStrength.EXCELLENT
    rssi >= -70 -> SignalStrength.GOOD
    rssi >= -85 -> SignalStrength.FAIR
    else -> SignalStrength.WEAK
}

private fun getTimeSince(timestamp: Long, currentTime: Long): String {
    val diff = currentTime - timestamp
    return when {
        diff < 1000 -> "Just now"
        diff < 60_000 -> "${diff / 1000}s ago"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3_600_000}h ago"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
