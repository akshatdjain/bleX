package com.blegod.app.ui.screens.configurator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blegod.app.data.ApiService
import com.blegod.app.data.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesTab() {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var apiZones by remember { mutableStateOf<List<ApiService.ApiZone>>(emptyList()) }
    var registeredScanners by remember { mutableStateOf<List<ApiService.ApiScanner>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Create zone dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var newZoneName by remember { mutableStateOf("") }
    var newZoneDesc by remember { mutableStateOf("") }

    // Rename zone dialog
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameZone by remember { mutableStateOf<ApiService.ApiZone?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameDescText by remember { mutableStateOf("") }

    // Delete confirmation
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteZoneTarget by remember { mutableStateOf<ApiService.ApiZone?>(null) }

    // Assign/unassign confirmation
    var showAssignConfirm by remember { mutableStateOf(false) }
    var assignAction by remember { mutableStateOf<Triple<Int, Int, Boolean>?>(null) } // zoneId, scannerId, isAssign
    var assignLabel by remember { mutableStateOf("") }

    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshData() {
        scope.launch {
            if (!isRefreshing) isLoading = true
            isRefreshing = true
            errorMsg = null
            ApiService.configuredBaseUrl = settings.apiBaseUrl
            try {
                apiZones = ApiService.getZones()
                registeredScanners = ApiService.getScanners()
            } catch (e: Exception) {
                errorMsg = "API error: ${e.message}"
            }
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { refreshData() }

    // ── Create Zone Dialog ──
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            icon = { Icon(Icons.Default.AddLocationAlt, null) },
            title = { Text("Create Zone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newZoneName,
                        onValueChange = { newZoneName = it },
                        label = { Text("Zone Name (e.g. Warehouse A)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newZoneDesc,
                        onValueChange = { newZoneDesc = it },
                        label = { Text("Description (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newZoneName.isNotBlank()) {
                        scope.launch {
                            try {
                                ApiService.createZone(newZoneName.trim(), newZoneDesc.trim().ifBlank { null })
                                newZoneName = ""; newZoneDesc = ""
                                showAddDialog = false
                                refreshData()
                            } catch (e: Exception) { errorMsg = "Create failed: ${e.message}" }
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Rename Zone Dialog ──
    if (showRenameDialog && renameZone != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text("Rename Zone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Zone Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = renameDescText,
                        onValueChange = { renameDescText = it },
                        label = { Text("Description (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank()) {
                        scope.launch {
                            try {
                                ApiService.updateZone(renameZone!!.id, renameText.trim(), renameDescText.trim().ifBlank { null })
                                showRenameDialog = false
                                refreshData()
                            } catch (e: Exception) { errorMsg = "Rename failed: ${e.message}" }
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Delete Confirmation ──
    if (showDeleteConfirm && deleteZoneTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Zone?") },
            text = {
                Text("Are you sure you want to delete \"${deleteZoneTarget!!.zoneName}\"? This will unassign all scanners from this zone. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                ApiService.deleteZone(deleteZoneTarget!!.id)
                                showDeleteConfirm = false
                                refreshData()
                            } catch (e: Exception) { errorMsg = "Delete failed: ${e.message}" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // ── Assign/Unassign Confirmation ──
    if (showAssignConfirm && assignAction != null) {
        val (zId, sId, isAssign) = assignAction!!
        AlertDialog(
            onDismissRequest = { showAssignConfirm = false },
            icon = { Icon(if (isAssign) Icons.Default.LinkOff else Icons.Default.Link, null) },
            title = { Text(if (isAssign) "Assign Scanner?" else "Remove Scanner?") },
            text = { Text(assignLabel) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            if (isAssign) ApiService.assignScannerToZone(zId, sId)
                            else ApiService.unassignScannerFromZone(zId, sId)
                            showAssignConfirm = false
                            refreshData()
                        } catch (e: Exception) { errorMsg = "Failed: ${e.message}" }
                    }
                }) { Text(if (isAssign) "Assign" else "Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showAssignConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            if (settings.apiBaseUrl.isNotBlank()) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add Zone") }
                )
            }
        }
    ) { padding ->
        if (settings.apiBaseUrl.isBlank()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("API not configured", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Double-tap the Configurator title\nto set the API URL.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
            }
        } else if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (apiZones.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Map, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("No zones yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Zones logically group your scanners.\nTap + to create your first zone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                errorMsg?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { refreshData() }) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh")
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { refreshData() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    errorMsg?.let { msg ->
                        item {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            ) {
                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                    items(apiZones, key = { it.id }) { zone ->
                        ApiZoneCard(
                            zone = zone,
                            allScanners = registeredScanners,
                            onRename = {
                                renameZone = zone
                                renameText = zone.zoneName
                                renameDescText = zone.description ?: ""
                                showRenameDialog = true
                            },
                            onDelete = {
                                deleteZoneTarget = zone
                                showDeleteConfirm = true
                            },
                            onAssign = { scannerId, scannerLabel ->
                                assignAction = Triple(zone.id, scannerId, true)
                                assignLabel = "Assign \"$scannerLabel\" to zone \"${zone.zoneName}\"?"
                                showAssignConfirm = true
                            },
                            onUnassign = { scannerId, scannerLabel ->
                                assignAction = Triple(zone.id, scannerId, false)
                                assignLabel = "Remove \"$scannerLabel\" from zone \"${zone.zoneName}\"?"
                                showAssignConfirm = true
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiZoneCard(
    zone: ApiService.ApiZone,
    allScanners: List<ApiService.ApiScanner>,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAssign: (Int, String) -> Unit,
    onUnassign: (Int, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val assignedIds = zone.scanners.map { it.id }.toSet()
    val unassignedScanners = allScanners.filter { it.id !in assignedIds }
    var showAddDropdown by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(zone.zoneName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        zone.description?.let {
                            if (it.isNotBlank()) Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle scanners")
                    }
                }
            }

            // ── Assigned Scanners (Chips) ──
            if (zone.scanners.isEmpty()) {
                Text("No scanners assigned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    zone.scanners.forEach { scanner ->
                        val label = scanner.name ?: scanner.macId
                        InputChip(
                            selected = true,
                            onClick = { onUnassign(scanner.id, label) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    if (scanner.type == "pi") Icons.Default.Router else Icons.Default.Sensors,
                                    null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }

            // ── Expanded: Add Scanner + Delete ──
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Scanner Button
                    Box {
                        FilledTonalButton(
                            onClick = { showAddDropdown = true },
                            enabled = unassignedScanners.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (unassignedScanners.isEmpty()) "All assigned" else "Add Scanner")
                        }
                        DropdownMenu(
                            expanded = showAddDropdown,
                            onDismissRequest = { showAddDropdown = false }
                        ) {
                            unassignedScanners.forEach { scanner ->
                                val label = scanner.name ?: scanner.macId
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text("${scanner.macId} • ${scanner.type ?: "?"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (scanner.type == "pi") Icons.Default.Router else Icons.Default.Sensors,
                                            null, modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showAddDropdown = false
                                        onAssign(scanner.id, label)
                                    }
                                )
                            }
                        }
                    }

                    // Delete Zone
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete Zone")
                    }
                }
            }
        }
    }
}
