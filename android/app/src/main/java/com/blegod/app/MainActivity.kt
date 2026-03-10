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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blegod.app.data.SettingsManager
import com.blegod.app.ui.DrawerContent
import com.blegod.app.ui.screens.LogScreen
import com.blegod.app.ui.screens.ScannerScreen
import com.blegod.app.ui.screens.SettingsScreen
import com.blegod.app.ui.screens.ConfiguratorScreen
import com.blegod.app.ui.theme.BleGodTheme
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
            BleGodTheme {
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
    val logsVisible by remember { derivedStateOf { settings.logsVisible } }

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
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                currentRoute == "scanner" -> "Scanner"
                                currentRoute == "settings" || currentRoute.startsWith("settings/") -> "Settings"
                                currentRoute == "config/hotspot" -> "Hotspot"
                                currentRoute == "config/scanners" -> "Scanners"
                                currentRoute == "config/zones" -> "Zones"
                                currentRoute == "config/assets" -> "Assets"
                                currentRoute == "logs" -> "Logs"
                                else -> "BleGod"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentRoute != "scanner" && currentRoute != "settings" && currentRoute != "logs") {
                                navController.popBackStack()
                            } else {
                                scope.launch { drawerState.open() }
                            }
                        }) {
                            Icon(
                                if (currentRoute == "scanner" || currentRoute == "settings" || currentRoute == "logs")
                                    Icons.Default.Menu
                                else Icons.Default.ArrowBack,
                                contentDescription = "Navigation"
                            )
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
                composable("config/hotspot") { ConfiguratorScreen(initialTab = 0) }
                composable("config/scanners") { ConfiguratorScreen(initialTab = 1) }
                composable("config/zones") { ConfiguratorScreen(initialTab = 2) }
                composable("config/assets") { ConfiguratorScreen(initialTab = 3) }
                composable("logs") { LogScreen() }
            }
        }
    }
}
