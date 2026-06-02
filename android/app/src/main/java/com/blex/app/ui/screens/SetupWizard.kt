package com.blex.app.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blex.app.AppConfig
import com.blex.app.data.ApiService
import com.blex.app.data.ScanRepository
import com.blex.app.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * SetupWizard — Progressive disclosure onboarding for BleX.
 *
 * Steps:
 * 0. Welcome
 * 1. Create your first zone
 * 2. Connect a scanner (this tablet)
 * 3. Register your first asset (beacon)
 * 4. Done!
 *
 * Design inspiration: Linear, Notion, Slack — minimal, clear progress, skip option always available.
 */

@Composable
fun SetupWizard(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsManager.getInstance(context) }
    val beacons by ScanRepository.beacons.collectAsState()

    // Wire ApiService immediately (not in LaunchedEffect) so skip works correctly
    ApiService.configuredBaseUrl = settings.apiBaseUrl
    ApiService.tenantId = settings.tenantId

    var currentStep by remember { mutableStateOf(0) }
    var createdZoneId by remember { mutableStateOf<Int?>(null) }
    var createdZoneName by remember { mutableStateOf("") }
    var createdScannerId by remember { mutableStateOf<Int?>(null) }
    var createdAssetMac by remember { mutableStateOf("") }
    var createdAssetName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Step Indicator ────────────────────────────────────
            if (currentStep > 0 && currentStep < 4) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isActive = index <= currentStep
                        val isCurrent = index == currentStep
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary
                                    else if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                        if (index < 3) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }

            // ── AnimatedContent for smooth step transitions ────────
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                        animationSpec = spring(),
                        initialOffsetX = { 150 }
                    ) togetherWith fadeOut(animationSpec = tween(150)) + slideOutHorizontally(
                        animationSpec = spring(),
                        targetOffsetX = { -150 }
                    )
                },
                label = "SetupWizardStep"
            ) { step ->
                when (step) {
                    0 -> WelcomeStep(
                        onContinue = { currentStep = 1 },
                        onSkip = {
                            settings.getPrefs().edit().putBoolean("setup_completed", true).apply()
                            onComplete()
                        }
                    )
                    1 -> CreateZoneStep(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onCreated = { zoneId, zoneName ->
                            createdZoneId = zoneId
                            createdZoneName = zoneName
                            currentStep = 2
                        },
                        onSkip = {
                            currentStep = 2
                        },
                        scope = scope,
                        settings = settings,
                        setLoading = { isLoading = it },
                        setError = { errorMessage = it }
                    )
                    2 -> ConnectScannerStep(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        createdZoneId = createdZoneId,
                        createdZoneName = createdZoneName,
                        onConnected = { scannerId ->
                            createdScannerId = scannerId
                            currentStep = 3
                        },
                        onSkip = {
                            currentStep = 3
                        },
                        scope = scope,
                        context = context,
                        settings = settings,
                        setLoading = { isLoading = it },
                        setError = { errorMessage = it }
                    )
                    3 -> RegisterAssetStep(
                        beacons = beacons,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onRegistered = { mac, name ->
                            createdAssetMac = mac
                            createdAssetName = name
                            currentStep = 4
                        },
                        onSkip = {
                            currentStep = 4
                        },
                        scope = scope,
                        settings = settings,
                        setLoading = { isLoading = it },
                        setError = { errorMessage = it }
                    )
                    4 -> DoneStep(
                        zoneName = createdZoneName,
                        assetName = createdAssetName,
                        onDone = {
                            settings.getPrefs().edit().putBoolean("setup_completed", true).apply()
                            onComplete()
                        }
                    )
                }
            }
        }

        // ── Error Toast ──────────────────────────────────────────
        if (errorMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                action = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(errorMessage ?: "")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// STEP 0: WELCOME
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun WelcomeStep(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // ── BleX Sunburst Logo (SVG-inspired Compose drawing) ───
        BleXSunburstLogo()

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Welcome to BleX",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Let's get your tracking set up.\nTakes about 3 minutes.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Let's Go →", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Skip setup",
            modifier = Modifier
                .clickable { onSkip() }
                .padding(12.dp),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
        )
    }
}

@Composable
private fun BleXSunburstLogo(modifier: Modifier = Modifier) {
    // Capture color before entering DrawScope (Canvas doesn't have @Composable context)
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(120.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(80.dp)
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = 30.dp.toPx()

            // Sunburst rays
            for (i in 0 until 12) {
                val angle = (i * 30).toFloat() * (Math.PI.toFloat() / 180f)
                val x1 = centerX + (radius * 0.5f) * kotlin.math.cos(angle)
                val y1 = centerY + (radius * 0.5f) * kotlin.math.sin(angle)
                val x2 = centerX + radius * kotlin.math.cos(angle)
                val y2 = centerY + radius * kotlin.math.sin(angle)
                drawLine(
                    color = primaryColor,
                    start = androidx.compose.ui.geometry.Offset(x1, y1),
                    end = androidx.compose.ui.geometry.Offset(x2, y2),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Center circle
            drawCircle(
                color = primaryColor,
                radius = 8.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// STEP 1: CREATE ZONE
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CreateZoneStep(
    isLoading: Boolean,
    errorMessage: String?,
    onCreated: (zoneId: Int, zoneName: String) -> Unit,
    onSkip: () -> Unit,
    scope: CoroutineScope,
    settings: SettingsManager,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit
) {
    var zoneName by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Where do you want to track?",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "A zone is a physical area — a room, floor, or building section.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = zoneName,
            onValueChange = { zoneName = it },
            label = { Text("Zone name") },
            placeholder = { Text("e.g. Warehouse A, ICU, Floor 2") },
            enabled = !isLoading && !showSuccess,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!showSuccess) {
            Button(
                onClick = {
                    if (zoneName.isBlank()) {
                        setError("Please enter a zone name")
                        return@Button
                    }
                    setLoading(true)
                    scope.launch {
                        try {
                            val response = ApiService.createZone(zoneName)
                            val zoneId = response.optInt("id", -1)
                            if (zoneId > 0) {
                                showSuccess = true
                                kotlinx.coroutines.delay(1000)
                                onCreated(zoneId, zoneName)
                            } else {
                                setError("Failed to create zone")
                            }
                        } catch (e: Exception) {
                            setError(e.message ?: "Failed to create zone")
                        } finally {
                            setLoading(false)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = zoneName.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Zone →", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "$zoneName created",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Skip",
            modifier = Modifier
                .clickable { onSkip() }
                .padding(12.dp),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// STEP 2: CONNECT SCANNER
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ConnectScannerStep(
    isLoading: Boolean,
    errorMessage: String?,
    createdZoneId: Int?,
    createdZoneName: String,
    onConnected: (scannerId: Int) -> Unit,
    onSkip: () -> Unit,
    scope: CoroutineScope,
    context: Context,
    settings: SettingsManager,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit
) {
    var showSuccess by remember { mutableStateOf(false) }
    val deviceMac = remember { AppConfig.getTabletMac(context) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.Router,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Connect your node",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Your Android tablet is already a scanner. Tap below to register it.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.TabletMac,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "This Tablet",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    deviceMac,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!showSuccess) {
            Button(
                onClick = {
                    setLoading(true)
                    scope.launch {
                        try {
                            val scannerResp = ApiService.upsertScanner(
                                mac = deviceMac,
                                name = android.os.Build.MODEL,
                                type = "android"
                            )
                            val scannerId = scannerResp.optInt("id", -1)
                            if (scannerId > 0 && createdZoneId != null) {
                                ApiService.assignScannerToZone(createdZoneId, scannerId)
                                showSuccess = true
                                kotlinx.coroutines.delay(1000)
                                onConnected(scannerId)
                            } else {
                                setError("Failed to register scanner")
                            }
                        } catch (e: Exception) {
                            setError(e.message ?: "Failed to register scanner")
                        } finally {
                            setLoading(false)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Register This Tablet →", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Tablet registered",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Skip",
            modifier = Modifier
                .clickable { onSkip() }
                .padding(12.dp),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
        )
    }
}


// ═══════════════════════════════════════════════════════════════════════════
// STEP 3: REGISTER ASSET
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun RegisterAssetStep(
    beacons: List<com.blex.app.BeaconData>,
    isLoading: Boolean,
    errorMessage: String?,
    onRegistered: (mac: String, name: String) -> Unit,
    onSkip: () -> Unit,
    scope: CoroutineScope,
    settings: SettingsManager,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit
) {
    var selectedBeaconMac by remember { mutableStateOf<String?>(null) }
    var assetName by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.Tag,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Tag your first asset",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Hold a beacon near your tablet. It will appear below.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Live beacon list
        if (beacons.isEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Searching for beacons...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Hold a BLE beacon close to this tablet",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                beacons.take(3).forEach { beacon ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedBeaconMac = beacon.mac }
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (selectedBeaconMac == beacon.mac)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    beacon.name ?: beacon.mac,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    beacon.mac,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    "${beacon.rssi} dBm",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selectedBeaconMac == beacon.mac) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Always show Next — registering is optional
        if (!showSuccess) {
            Button(
                onClick = {
                    if (selectedBeaconMac != null) {
                        setLoading(true)
                        scope.launch {
                            try {
                                ApiService.registerAsset(
                                    mac = selectedBeaconMac!!,
                                    name = assetName.ifBlank { null }
                                )
                                showSuccess = true
                                kotlinx.coroutines.delay(800)
                                onRegistered(selectedBeaconMac!!, assetName.ifBlank { selectedBeaconMac!! })
                            } catch (e: Exception) {
                                // Even if registration fails, proceed
                                onRegistered(selectedBeaconMac!!, assetName.ifBlank { selectedBeaconMac!! })
                            } finally {
                                setLoading(false)
                            }
                        }
                    } else {
                        onSkip()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (selectedBeaconMac != null) "Register & Next →" else "Next →",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else if (showSuccess) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Asset registered",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Skip",
            modifier = Modifier
                .clickable { onSkip() }
                .padding(12.dp),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// STEP 4: DONE
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun DoneStep(
    zoneName: String,
    assetName: String,
    onDone: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "You're all set!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                SummaryRow("Zone", if (zoneName.isNotEmpty()) zoneName else "Skipped")
                Spacer(modifier = Modifier.height(12.dp))
                SummaryRow("Asset", if (assetName.isNotEmpty()) assetName else "Skipped")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Your first movement will appear on the dashboard within seconds of the beacon entering a zone.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Go to Dashboard →", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Text(
            value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

