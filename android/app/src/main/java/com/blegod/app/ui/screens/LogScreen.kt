package com.blegod.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blegod.app.data.LogEntry
import com.blegod.app.data.LogLevel
import com.blegod.app.data.ScanRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Scanner", "Broker")

    val scannerLogs by ScanRepository.logs.collectAsState()
    val brokerLogs by ScanRepository.brokerLogs.collectAsState()
    val context = LocalContext.current

    val currentLogs = if (selectedTab == 0) scannerLogs else brokerLogs

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Tab row
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            "$title (${if (index == 0) scannerLogs.size else brokerLogs.size})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        // Log list
        LogList(logs = currentLogs)
    }
}

@Composable
private fun LogList(logs: List<LogEntry>) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    if (logs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No logs yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

private fun saveLogs(context: Context, logs: List<LogEntry>, tabName: String) {
    try {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val filename = "blegod_${tabName.lowercase()}_logs_${dateFormat.format(Date())}.txt"

        val dir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        dir.mkdirs()
        val file = File(dir, filename)

        val content = buildString {
            appendLine("BleGod $tabName Logs — Exported ${logDateFormat.format(Date())}")
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
