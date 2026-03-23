package com.blegod.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blegod.app.data.SettingsManager
import com.blegod.app.ui.DrawerContent
import com.blegod.app.ui.screens.DashboardWebScreen
import com.blegod.app.ui.screens.LogScreen
import com.blegod.app.ui.screens.ScannerScreen
import com.blegod.app.ui.screens.SettingsScreen
import com.blegod.app.ui.screens.configurator.AssetsTab
import com.blegod.app.ui.screens.configurator.HotspotTab
import com.blegod.app.ui.screens.configurator.ScannersTab
import com.blegod.app.ui.screens.configurator.ZonesTab
import com.blegod.app.ui.theme.BleXTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        private val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            requestBackgroundLocation()
        } else {
            Toast.makeText(this, "Permissions required for BLE scanning", Toast.LENGTH_LONG).show()
            requestPermissions()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Log.i(TAG, "Background location granted")
        else Log.w(TAG, "Background location denied")
        requestNotificationPermission()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i(TAG, "Notification permission ${if (granted) "granted" else "denied"}")
        startServiceAndUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (hasAllPermissions()) {
            startServiceAndUI()
        } else {
            requestPermissions()
        }
    }

    private fun hasAllPermissions(): Boolean =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestPermissions() {
        permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun requestBackgroundLocation() {
        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    // Request POST_NOTIFICATIONS on Android 13+ (API 33).
    // Without this, the app "doesn't send notifications" per system info.
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startServiceAndUI()
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Battery opt exemption request failed", e)
        }
    }

    private fun startServiceAndUI() {
        // Start the foreground service
        val intent = Intent(this, BleScannerService::class.java)
        startForegroundService(intent)
        Log.i(TAG, "Service started")

        requestBatteryOptimizationExemption()

        // Set up Compose UI
        setContent {
            BleXTheme {
                BleGodNavHost()
            }
        }
    }
}

// ── Main App Composable with Navigation Drawer ──────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleGodNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "scanner"
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val logsVisible by settings.logsVisibleFlow.collectAsState()

    // Handle deep link routing from Intent
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity
        val navigateTo = activity?.intent?.getStringExtra("navigate_to")
        if (navigateTo != null) {
            navController.navigate(navigateTo) {
                popUpTo("scanner") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            activity.intent?.removeExtra("navigate_to") // Clear so we don't re-navigate on rotation
        }
    }

    // Bluetooth check dialog
    var showBluetoothDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        if (adapter != null && !adapter.isEnabled) {
            showBluetoothDialog = true
        }
    }

    if (showBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDialog = false },
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = null) },
            title = { Text("Bluetooth Required") },
            text = { Text("BleGod needs Bluetooth to scan for BLE beacons. Please enable Bluetooth to continue.") },
            confirmButton = {
                Button(onClick = {
                    showBluetoothDialog = false
                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }) {
                    Text("Enable Bluetooth")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBluetoothDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    // Configurator first-time pop-up
    var hasSeenConfigPopup by remember { mutableStateOf(settings.getPrefs().getBoolean("has_seen_config", false)) }
    if (currentRoute.startsWith("config/") && !hasSeenConfigPopup) {
        AlertDialog(
            onDismissRequest = {
                hasSeenConfigPopup = true
                settings.getPrefs().edit().putBoolean("has_seen_config", true).apply()
            },
            icon = { Icon(Icons.Default.Build, contentDescription = null) },
            title = { Text("Welcome to Configurator") },
            text = { Text("Here you can easily edit, rename, add, and delete your Zones, Assets, and Scanners. Simply configure the settings to personalize your BLE network.") },
            confirmButton = {
                Button(onClick = {
                    hasSeenConfigPopup = true
                    settings.getPrefs().edit().putBoolean("has_seen_config", true).apply()
                }) {
                    Text("Okay")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                currentRoute = currentRoute,
                logsVisible = logsVisible,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo("scanner") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        },
        gesturesEnabled = currentRoute != "webdashboard"
    ) {
        Scaffold(
            topBar = {
                var configDropdownExpanded by remember { mutableStateOf(false) }
                TopAppBar(
                    title = {
                        if (currentRoute.startsWith("config/")) {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { configDropdownExpanded = true }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        when (currentRoute) {
                                            "config/hotspot" -> "Hotspot"
                                            "config/scanners" -> "Scanners"
                                            "config/zones" -> "Zones"
                                            "config/assets" -> "Assets"
                                            else -> "Configurator"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Config Page"
                                    )
                                }
                                DropdownMenu(
                                    expanded = configDropdownExpanded,
                                    onDismissRequest = { configDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Hotspot", fontWeight = if(currentRoute=="config/hotspot") FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { configDropdownExpanded = false; navController.navigate("config/hotspot") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Scanners", fontWeight = if(currentRoute=="config/scanners") FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { configDropdownExpanded = false; navController.navigate("config/scanners") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Zones", fontWeight = if(currentRoute=="config/zones") FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { configDropdownExpanded = false; navController.navigate("config/zones") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Assets", fontWeight = if(currentRoute=="config/assets") FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { configDropdownExpanded = false; navController.navigate("config/assets") }
                                    )
                                }
                            }
                        } else {
                            Text(
                                when {
                                    currentRoute == "scanner" -> "Dashboard"
                                    currentRoute == "settings" || currentRoute.startsWith("settings/") -> "Settings"
                                    currentRoute == "logs" -> "Logs"
                                    currentRoute == "webdashboard" -> "Web Dashboard"
                                    else -> "BleGod"
                                }
                            )
                        }
                    },
                    navigationIcon = {
                        val isTopLevel = currentRoute == "scanner" ||
                            currentRoute == "settings" ||
                            currentRoute == "logs" ||
                            currentRoute == "webdashboard" ||
                            currentRoute.startsWith("config/")
                        IconButton(onClick = {
                            if (!isTopLevel) {
                                navController.popBackStack()
                            } else {
                                scope.launch { drawerState.open() }
                            }
                        }) {
                            Icon(
                                if (isTopLevel) Icons.Default.Menu else Icons.Default.ArrowBack,
                                contentDescription = "Navigation"
                            )
                        }
                    },
                    actions = {
                        if (currentRoute.startsWith("config/")) {
                            var showInfo by remember { mutableStateOf(false) }
                            IconButton(onClick = { showInfo = true }) {
                                Icon(Icons.Default.Info, contentDescription = "Info")
                            }
                            if (showInfo) {
                                AlertDialog(
                                    onDismissRequest = { showInfo = false },
                                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    title = {
                                        Text(when(currentRoute) {
                                            "config/hotspot" -> "Hotspot Info"
                                            "config/scanners" -> "Scanners Info"
                                            "config/zones" -> "Zones Info"
                                            "config/assets" -> "Assets Info"
                                            else -> "Info"
                                        })
                                    },
                                    text = {
                                        Text(when(currentRoute) {
                                            "config/hotspot" -> "The Hotspot enables you to connect to the scanners over Wi-Fi, allowing you to seamlessly share and provision data across your entire network."
                                            "config/scanners" -> "Manage your physical scanner hardware. You can securely assign static IPs, check their connection health, or push setting over-the-air updates."
                                            "config/zones" -> "Organize your scanners into different physical sections or zones. This helps in pinpointing where a particular beacon is broadcasting from."
                                            "config/assets" -> "Assign memorable human-readable tags and icons to specific beacon MAC addresses, making it dramatically easier to track your items."
                                            else -> ""
                                        })
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showInfo = false }) { Text("Got it") }
                                    }
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "scanner",
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    fadeIn(animationSpec = tween(250)) +
                    slideInHorizontally(animationSpec = tween(250)) { it / 4 }
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(250)) +
                    slideInHorizontally(animationSpec = tween(250)) { -it / 4 }
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(200)) +
                    slideOutHorizontally(animationSpec = tween(200)) { it / 4 }
                }
            ) {
                composable("scanner") { ScannerScreen() }
                composable("settings") { SettingsScreen() }
                composable("config/hotspot") { HotspotTab() }
                composable("config/scanners") { ScannersTab() }
                composable("config/zones") { ZonesTab() }
                composable("config/assets") { AssetsTab() }
                composable("logs") { LogScreen() }
                composable("webdashboard") { DashboardWebScreen() }
            }
        }
    }
}
