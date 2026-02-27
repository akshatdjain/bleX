package com.blegod.app

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blegod.app.ui.screens.LogScreen
import com.blegod.app.ui.screens.ScannerScreen
import com.blegod.app.ui.screens.SettingsScreen
import com.blegod.app.ui.screens.ConfiguratorScreen
import com.blegod.app.ui.theme.BleGodTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "BleGod.Main"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.all { it.value }
        if (allGranted) {
            Log.i(TAG, "Base permissions granted")
            requestBackgroundLocation()
        } else {
            Log.w(TAG, "Some permissions denied")
            Toast.makeText(this, "BleGod needs all permissions for BLE scanning", Toast.LENGTH_LONG).show()
            requestNotificationPermission()
        }
    }

    private val bgLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Log.i(TAG, "Background location granted")
        else Log.w(TAG, "Background location denied")
        requestNotificationPermission()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Log.i(TAG, "Notification permission granted")
        else Log.w(TAG, "Notification permission denied - notifications won't show")
        startServiceAndUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (hasAllPermissions()) {
            requestBatteryOptimizationExemption()
            startServiceAndUI()
        } else {
            requestPermissions()
        }
    }

    private fun hasAllPermissions(): Boolean {
        val perms = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    private fun requestBackgroundLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestNotificationPermission()
        }
    }

    /**
     * Request POST_NOTIFICATIONS on Android 13+ (API 33).
     * Without this, the app "doesn't send notifications" per system info.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startServiceAndUI()
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                })
            } catch (e: Exception) {
                Log.e(TAG, "Battery exemption failed: ${e.message}")
            }
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

// ── Main App Composable with Bottom Navigation ──────────────────

@Composable
fun BleGodNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "scanner",
                    onClick = {
                        navController.navigate("scanner") {
                            popUpTo("scanner") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    icon = { Icon(Icons.Default.Bluetooth, "Scanner") },
                    label = { Text("Scanner") }
                )
                NavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo("scanner")
                            launchSingleTop = true
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") }
                )
                NavigationBarItem(
                    selected = currentRoute == "configurator",
                    onClick = {
                        navController.navigate("configurator") {
                            popUpTo("scanner")
                            launchSingleTop = true
                        }
                    },
                    icon = { Icon(Icons.Default.Build, "Config") },
                    label = { Text("Config") }
                )
                NavigationBarItem(
                    selected = currentRoute == "logs",
                    onClick = {
                        navController.navigate("logs") {
                            popUpTo("scanner")
                            launchSingleTop = true
                        }
                    },
                    icon = { Icon(Icons.Default.Terminal, "Logs") },
                    label = { Text("Logs") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "scanner",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("scanner") { ScannerScreen() }
            composable("settings") { SettingsScreen() }
            composable("configurator") { ConfiguratorScreen() }
            composable("logs") { LogScreen() }
        }
    }
}
