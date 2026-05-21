package com.blex.app.ui.screens

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blex.app.AppConfig
import com.blex.app.BatteryMonitor
import com.blex.app.ServiceHealth
import com.blex.app.data.LogEntry
import com.blex.app.data.LogLevel
import com.blex.app.data.ScanRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ── Tab indices ───────────────────────────────────────────────
private const val TAB_SCANNER = 0
private const val TAB_BROKER = 1
private const val TAB_SYSTEM = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Scanner", "Broker", "System")

    val scannerLogs by ScanRepository.logs.collectAsState()
    val brokerLogs by ScanRepository.brokerLogs.collectAsState()
    val context = LocalContext.current

    // Per-tab level filter state (null = ALL)
    var scannerLevelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var brokerLevelFilter by remember { mutableStateOf<LogLevel?>(null) }

    // Auto-scroll toggle state (per tab)
    var scannerAutoScroll by remember { mutableStateOf(true) }
    var brokerAutoScroll by remember { mutableStateOf(true) }

    // Loading state for scanner and broker logs (show skeletons for first 2 seconds)
    var isLoadingScannerLogs by remember { mutableStateOf(true) }
    var isLoadingBrokerLogs by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(220)
        isLoadingScannerLogs = false
        isLoadingBrokerLogs = false
    }

    val currentLogs = when (selectedTab) {
        TAB_SCANNER -> scannerLogs
        TAB_BROKER -> brokerLogs
        else -> emptyList()
    }

    val currentLevelFilter = when (selectedTab) {
        TAB_SCANNER -> scannerLevelFilter
        TAB_BROKER -> brokerLevelFilter
        else -> null
    }

    val setCurrentLevelFilter: (LogLevel?) -> Unit = { level ->
        when (selectedTab) {
            TAB_SCANNER -> scannerLevelFilter = level
            TAB_BROKER -> brokerLevelFilter = level
        }
    }

    val currentAutoScroll = when (selectedTab) {
        TAB_SCANNER -> scannerAutoScroll
        TAB_BROKER -> brokerAutoScroll
        else -> false
    }

    val setCurrentAutoScroll: (Boolean) -> Unit = { value ->
        when (selectedTab) {
            TAB_SCANNER -> scannerAutoScroll = value
            TAB_BROKER -> brokerAutoScroll = value
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab row with counts
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        val label = when (index) {
                            TAB_SCANNER -> "$title (${scannerLogs.size})"
                            TAB_BROKER -> "$title (${brokerLogs.size})"
                            else -> title
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                )
            }
        }

        when (selectedTab) {
            TAB_SCANNER, TAB_BROKER -> {
                // Controls row: filter chips + auto-scroll toggle + clear + export
                LogControlsRow(
                    levelFilter = currentLevelFilter,
                    onLevelFilterChange = setCurrentLevelFilter,
                    autoScroll = currentAutoScroll,
                    onAutoScrollChange = setCurrentAutoScroll,
                    onClear = {
                        if (selectedTab == TAB_SCANNER) ScanRepository.clearLogs()
                        else ScanRepository.clearBrokerLogs()
                    },
                    onExport = {
                        saveLogs(context, currentLogs, tabs[selectedTab])
                    }
                )

                val displayedLogs = remember(currentLogs, currentLevelFilter) {
                    if (currentLevelFilter == null) currentLogs
                    else currentLogs.filter { it.level == currentLevelFilter }
                }

                val isLoading = when (selectedTab) {
                    TAB_SCANNER -> isLoadingScannerLogs
                    TAB_BROKER -> isLoadingBrokerLogs
                    else -> false
                }
                LogList(
                    logs = displayedLogs,
                    levelFilter = currentLevelFilter,
                    onClearFilter = { setCurrentLevelFilter(null) },
                    autoScroll = currentAutoScroll,
                    isLoading = isLoading
                )
            }
            TAB_SYSTEM -> {
                SystemTab()
            }
        }
    }
}

// ── Controls row: chips + auto-scroll + clear + export ────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogControlsRow(
    levelFilter: LogLevel?,
    onLevelFilterChange: (LogLevel?) -> Unit,
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Horizontally scrollable filter chips
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = levelFilter == null,
                onClick = { onLevelFilterChange(null) },
                label = { Text("ALL", style = MaterialTheme.typography.labelSmall) }
            )
            LogLevel.entries.forEach { level ->
                val chipColor = levelChipColor(level)
                FilterChip(
                    selected = levelFilter == level,
                    onClick = { onLevelFilterChange(if (levelFilter == level) null else level) },
                    label = { Text(level.name, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor.copy(alpha = 0.18f),
                        selectedLabelColor = chipColor
                    )
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        // Auto-scroll toggle chip
        FilterChip(
            selected = autoScroll,
            onClick = { onAutoScrollChange(!autoScroll) },
            label = { Text("Auto", style = MaterialTheme.typography.labelSmall) },
            leadingIcon = {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        )

        // Clear button
        IconButton(onClick = onClear) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = "Clear logs",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Export button
        IconButton(onClick = onExport) {
            Icon(
                Icons.Default.Save,
                contentDescription = "Export logs",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Log list with empty state ─────────────────────────────────

@Composable
private fun LogList(
    logs: List<LogEntry>,
    levelFilter: LogLevel?,
    onClearFilter: () -> Unit,
    autoScroll: Boolean,
    isLoading: Boolean = false
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    if (isLoading && logs.isEmpty()) {
        // Show skeleton loading state
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(6) {
                SkeletonLogRow()
            }
        }
    } else if (logs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (levelFilter != null) {
                // Filtered empty state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.FilterAlt,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "No ${levelFilter.name} logs",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onClearFilter) {
                        Text("Clear filter")
                    }
                }
            } else {
                // No logs at all
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "No logs yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(logs) { entry ->
                LogRow(entry)
            }
        }
    }
}

// ── Skeleton Log Row ────────────────────────────────────────────

@Composable
private fun SkeletonLogRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "log_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "log_shimmer_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp placeholder (60dp)
        Surface(
            modifier = Modifier
                .width(60.dp)
                .height(12.dp),
            shape = RoundedCornerShape(3.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {}
        Spacer(Modifier.width(6.dp))
        // Level placeholder (16dp)
        Surface(
            modifier = Modifier
                .width(16.dp)
                .height(12.dp),
            shape = RoundedCornerShape(3.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {}
        Spacer(Modifier.width(6.dp))
        // Message placeholder (fills remaining)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            shape = RoundedCornerShape(3.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {}
    }
}

// ── Log row (unchanged from original) ────────────────────────

@Composable
private fun LogRow(entry: LogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val color = when (entry.level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    val levelTag = when (entry.level) {
        LogLevel.DEBUG -> "D"
        LogLevel.INFO -> "I"
        LogLevel.WARN -> "W"
        LogLevel.ERROR -> "E"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            timeFormat.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
        Spacer(Modifier.width(6.dp))
        Text(
            levelTag,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
            fontSize = 11.sp
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${entry.tag}: ${entry.message}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            fontSize = 11.sp
        )
    }
}

// ── System tab ────────────────────────────────────────────────

@Composable
private fun SystemTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Battery section
        SystemSectionHeader(Icons.Default.BatteryStd, "Battery")
        SystemBatterySection()

        // Service health section
        SystemSectionHeader(Icons.Default.MonitorHeart, "Service Health")
        SystemServiceHealthSection()

        // Device info section
        SystemSectionHeader(Icons.Default.Info, "Device Info")
        SystemDeviceInfoSection()

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SystemSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── System Battery Section (duplicated from SettingsScreen BatteryPanel) ──

@Composable
private fun SystemBatterySection() {
    val context = LocalContext.current
    val serviceStatus by ScanRepository.serviceStatus.collectAsState()
    val battery = remember(serviceStatus.batteryLevel, serviceStatus.isCharging) {
        BatteryMonitor.getStats(context)
    }
    val drainRate = remember(serviceStatus.batteryLevel) {
        BatteryMonitor.getDrainRatePerHour()
    }
    val batteryColor = when {
        battery.level > 50 -> MaterialTheme.colorScheme.primary
        battery.level > 20 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Compact header: level + bar side by side
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "${battery.level}%",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = batteryColor
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { battery.level / 100f },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
                        color = batteryColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        if (battery.isCharging) "Charging • ${battery.plugType}" else "Discharging",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SystemInfoRow("Voltage", "${battery.voltage} V")
            SystemInfoRow("Temperature", "${battery.temperature}°C")
            SystemInfoRow("Health", battery.health)
            SystemInfoRow(
                "Drain Rate",
                if (battery.isCharging || drainRate <= 0f) "—" else "%.1f%% / hr".format(drainRate)
            )
        }
    }
}

// ── System Service Health Section (duplicated from SettingsScreen ServiceHealthPanel) ──

@Composable
private fun SystemServiceHealthSection() {
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
            SystemInfoRowWithIcon(
                Icons.Default.Timer, "Uptime",
                ServiceHealth.formatUptime(System.currentTimeMillis() - serviceStatus.startTime)
            )
            SystemInfoRowWithIcon(
                Icons.Default.Radar, "BLE Scanner",
                if (serviceStatus.isScanning) "Running" else "Stopped",
                if (serviceStatus.isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            SystemInfoRowWithIcon(
                Icons.Default.CloudQueue, "MQTT",
                if (serviceStatus.isMqttConnected) "Connected" else "Disconnected",
                if (serviceStatus.isMqttConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            SystemInfoRow("Scan Cycles", "${serviceStatus.scanCycleCount}")
            SystemInfoRow("Total Beacons Seen", "${serviceStatus.totalBeaconsScanned}")
            SystemInfoRow("Messages Published", "${serviceStatus.messagesPublished}")
            SystemInfoRow("Messages Failed", "${serviceStatus.messagesFailed}")

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            SystemInfoRowWithIcon(
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

// ── System Device Info Section (duplicated from SettingsScreen DeviceInfoPanel) ──

@Composable
private fun SystemDeviceInfoSection() {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SystemInfoRow("Device ID", AppConfig.getDeviceId(context))
            SystemInfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            SystemInfoRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
            SystemInfoRow("App Version", "3.0.6")
        }
    }
}

// ── System tab info row helpers ───────────────────────────────

@Composable
private fun SystemInfoRowWithIcon(
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
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// ── Level filter chip color helper ────────────────────────────

@Composable
private fun levelChipColor(level: LogLevel): Color = when (level) {
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.INFO -> MaterialTheme.colorScheme.primary
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
}

// ── saveLogs (unchanged from original) ───────────────────────

private fun saveLogs(context: Context, logs: List<LogEntry>, tabName: String) {
    try {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val filename = "blex_${tabName.lowercase()}_logs_${dateFormat.format(Date())}.txt"

        val dir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        dir.mkdirs()
        val file = File(dir, filename)

        val content = buildString {
            appendLine("BleX $tabName Logs — Exported ${logDateFormat.format(Date())}")
            appendLine("=".repeat(60))
            appendLine()
            for (entry in logs) {
                val time = logDateFormat.format(Date(entry.timestamp))
                val level = when (entry.level) {
                    LogLevel.DEBUG -> "DEBUG"
                    LogLevel.INFO -> "INFO "
                    LogLevel.WARN -> "WARN "
                    LogLevel.ERROR -> "ERROR"
                }
                appendLine("$time  $level  [${entry.tag}]  ${entry.message}")
            }
            appendLine()
            appendLine("Total entries: ${logs.size}")
        }

        file.writeText(content)
        Toast.makeText(context, "Saved to ${file.name}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
