package com.blegod.app.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blegod.app.BeaconData
import com.blegod.app.data.ScanRepository
import com.blegod.app.data.ServiceStatus
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
    val beacons by ScanRepository.beacons.collectAsState()
    val status by ScanRepository.serviceStatus.collectAsState()
    var selectedBeacon by remember { mutableStateOf<BeaconData?>(null) }

    // Sort & Filter state
    var sortMode by remember { mutableStateOf(SortMode.RSSI) }
    var filterMode by remember { mutableStateOf(FilterMode.ALL) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }

    // Apply filter
    val filteredBeacons = when (filterMode) {
        FilterMode.ALL -> beacons
        FilterMode.IBEACON -> beacons.filter { it.beaconType == "iBeacon" }
        FilterMode.EDDYSTONE -> beacons.filter { it.beaconType?.startsWith("Eddystone") == true }
    }

    // Apply sort
    val sortedBeacons = when (sortMode) {
        SortMode.RSSI -> filteredBeacons.sortedByDescending { it.rssi }
        SortMode.NAME -> filteredBeacons.sortedBy { it.name ?: "zzz" }
        SortMode.TYPE -> filteredBeacons.sortedBy { it.beaconType ?: "zzz" }
        SortMode.LAST_SEEN -> filteredBeacons.sortedByDescending { it.timestamp }
    }

    // Pull-to-refresh handler
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(500) // Brief visual feedback
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BleGod Scanner", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${sortedBeacons.size} devices nearby",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
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
                    StatusChip(status)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Stats bar
            StatsBar(status)

            // Filter chips
            FilterChipRow(filterMode) { filterMode = it }

            // Beacon list with pull-to-refresh
            if (sortedBeacons.isEmpty()) {
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
                        items(sortedBeacons, key = { it.mac }) { beacon ->
                            val registeredName = ScanRepository.getAssetName(beacon.mac)
                            BeaconCard(
                                beacon = beacon,
                                displayName = registeredName ?: beacon.name ?: "Unknown Device",
                                isRegistered = registeredName != null,
                                onClick = { selectedBeacon = beacon }
                            )
                        }
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

@Composable
private fun BeaconCard(
    beacon: BeaconData,
    displayName: String,
    isRegistered: Boolean,
    onClick: () -> Unit
) {
    val signalStrength = getSignalStrength(beacon.rssi)
    val timeSince = getTimeSince(beacon.timestamp)

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
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
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
                Text(
                    timeSince,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
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

private fun getTimeSince(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
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
