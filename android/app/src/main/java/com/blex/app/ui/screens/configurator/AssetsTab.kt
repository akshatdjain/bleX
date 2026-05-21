package com.blex.app.ui.screens.configurator

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blex.app.data.ApiService
import com.blex.app.BeaconData
import com.blex.app.data.ScanRepository
import com.blex.app.data.SettingsManager
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
    var isLoading by remember { mutableStateOf(true) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var registerMac by remember { mutableStateOf("") }
    var registerName by remember { mutableStateOf("") }
    var registerError by remember { mutableStateOf<String?>(null) }
    var isRegistering by remember { mutableStateOf(false) }

    // Edit / rename registered asset
    var showEditDialog by remember { mutableStateOf(false) }
    var editAsset by remember { mutableStateOf<ApiService.ApiAsset?>(null) }
    var editName by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    // Delete error snackbar
    var deleteError by remember { mutableStateOf<String?>(null) }

    // Delete confirmation
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<Int?>(null) }
    var deleteTargetName by remember { mutableStateOf("") }

    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshAssets() {
        scope.launch {
            if (!isRefreshing) isLoading = true
            isRefreshing = true
            val startTime = System.currentTimeMillis()
            ApiService.configuredBaseUrl = settings.apiBaseUrl
            registeredAssets = try { ApiService.getAssets() } catch (e: Exception) { emptyList() }
            ScanRepository.setRegisteredAssets(registeredAssets)
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 220) kotlinx.coroutines.delay(220 - elapsed)
            isLoading = false
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
            onDismissRequest = { if (!isRegistering) { showRegisterDialog = false; registerError = null } },
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRegistering
                    )
                    registerError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isRegistering,
                    onClick = {
                        scope.launch {
                            isRegistering = true
                            registerError = null
                            try {
                                ApiService.registerAsset(registerMac, registerName.trim().ifBlank { null })
                                showRegisterDialog = false
                                registerName = ""; registerError = null
                                refreshAssets()
                            } catch (e: Exception) {
                                val msg = e.message ?: "Registration failed"
                                // 409 = already registered — offer to update name instead
                                registerError = if (msg.contains("already", ignoreCase = true))
                                    "Already registered. Use Edit to rename it."
                                else msg
                            }
                            isRegistering = false
                        }
                    }
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Register")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRegistering,
                    onClick = { showRegisterDialog = false; registerError = null }
                ) { Text("Cancel") }
            }
        )
    }

    // Edit/rename dialog
    if (showEditDialog && editAsset != null) {
        AlertDialog(
            onDismissRequest = { if (!isEditing) { showEditDialog = false; editError = null } },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text("Rename Asset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(editAsset!!.bluetoothId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("New Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isEditing
                    )
                    editError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isEditing && editName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isEditing = true
                            editError = null
                            try {
                                ApiService.updateAsset(editAsset!!.id, editAsset!!.bluetoothId, editName.trim())
                                showEditDialog = false
                                editError = null
                                refreshAssets()
                            } catch (e: Exception) {
                                editError = e.message ?: "Update failed"
                            }
                            isEditing = false
                        }
                    }
                ) {
                    if (isEditing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isEditing, onClick = { showEditDialog = false; editError = null }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm && deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Asset?") },
            text = {
                Text(
                    "\"$deleteTargetName\" will be removed from tracking. Zone history is kept but the beacon will no longer be tracked.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = deleteTargetId ?: return@Button
                        showDeleteConfirm = false
                        scope.launch {
                            try {
                                ApiService.deleteAsset(id)
                                refreshAssets()
                            } catch (e: Exception) {
                                deleteError = "Delete failed: ${e.message}"
                            }
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

    // Delete error snackbar
    deleteError?.let { err ->
        LaunchedEffect(err) {
            kotlinx.coroutines.delay(3000)
            deleteError = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

        if (isLoading) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.width(120.dp).height(16.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                }
                items(4) {
                    SkeletonAssetCard()
                }
            }
        } else if (beacons.isEmpty()) {
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
                                registerError = null
                                showRegisterDialog = true
                            },
                            onEdit = { asset ->
                                editAsset = asset
                                editName = asset.assetName ?: ""
                                editError = null
                                showEditDialog = true
                            },
                            onDelete = { assetId, assetName ->
                                deleteTargetId = assetId
                                deleteTargetName = assetName
                                showDeleteConfirm = true
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Delete error snackbar overlay
    deleteError?.let { err ->
        androidx.compose.ui.layout.Layout(
            content = {},
            modifier = Modifier.fillMaxSize()
        ) { _, constraints -> layout(constraints.maxWidth, constraints.maxHeight) {} }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 4.dp
            ) {
                Text(
                    err,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    } // close outer Box
}

@Composable
fun SkeletonAssetCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "asset_skeleton_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "asset_skeleton_alpha"
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth().alpha(alpha),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier.width(150.dp).height(14.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                    Surface(
                        modifier = Modifier.width(110.dp).height(12.dp),
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier.width(50.dp).height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
                Surface(
                    modifier = Modifier.width(60.dp).height(12.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
            }
        }
    }
}

@Composable
fun BeaconAssetCard(
    beacon: BeaconData,
    registeredAsset: ApiService.ApiAsset?,
    onRegister: () -> Unit,
    onEdit: (ApiService.ApiAsset) -> Unit = {},
    onDelete: (Int, String) -> Unit
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${beacon.rssi} dBm",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = rssiColor
                )
                if (isRegistered && registeredAsset != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(
                            onClick = { onEdit(registeredAsset) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { onDelete(registeredAsset.id, registeredAsset.assetName ?: registeredAsset.bluetoothId) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    TextButton(onClick = onRegister, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Register", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
