package com.blex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blex.app.AppConfig
import com.blex.app.BuildConfig
import com.blex.app.data.ApiService
import com.blex.app.data.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var orgName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        // Logo / Title
        Text(
            text = "BleX",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Asset Tracking Platform",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = if (isRegister) "Create Account" else "Sign In",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Name field (register only)
        if (isRegister) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = orgName,
                onValueChange = { orgName = it },
                label = { Text("Organization / Site Name") },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                placeholder = { Text("e.g. City Hospital, Warehouse B") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show", fontSize = 12.sp)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Error message
        errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Primary action button
        Button(
            onClick = {
                errorMessage = null
                scope.launch {
                    isLoading = true
                    try {
                        val result = if (isRegister) {
                            if (name.isBlank() || orgName.isBlank()) {
                                errorMessage = "Name and organization are required"
                                isLoading = false
                                return@launch
                            }
                            ApiService.register(
                                name = name.trim(),
                                email = email.trim(),
                                password = password,
                                orgName = orgName.trim()
                            )
                        } else {
                            ApiService.login(
                                email = email.trim(),
                                password = password
                            )
                        }
                        // Save to SettingsManager
                        settings.authToken = result.accessToken
                        settings.tenantId = result.tenantId
                        settings.userName = result.name
                        settings.userEmail = result.email
                        settings.orgName = result.orgName
                        if (settings.apiBaseUrl.isEmpty()) {
                            settings.apiBaseUrl = AppConfig.REMOTE_API_URL
                        }
                        if (settings.webDashboardUrl == "http://93.127.206.7:9000" ||
                            settings.webDashboardUrl.isEmpty()) {
                            settings.webDashboardUrl = AppConfig.REMOTE_WEB_URL
                        }
                        // Auto-configure remote MQTT bridge (WSS to DGX via Caddy)
                        if (settings.remoteHost.isEmpty()) {
                            settings.remoteHost = AppConfig.REMOTE_MQTT_HOST
                            settings.remotePort = AppConfig.REMOTE_MQTT_PORT_WSS
                            settings.remoteTlsEnabled = true
                            settings.remoteUseWebSocket = true
                            settings.remoteWebSocketPath = AppConfig.REMOTE_MQTT_WSS_PATH
                            settings.remoteUsername = BuildConfig.MQTT_USERNAME
                            settings.remotePassword = BuildConfig.MQTT_PASSWORD
                        }
                        // Wire ApiService immediately so all CRUD calls use correct tenant
                        ApiService.configuredBaseUrl = settings.apiBaseUrl
                        ApiService.tenantId = result.tenantId
                        ApiService.authToken = result.accessToken

                        // Auto-fetch master Pi IP for local master model
                        // If a Pi master has registered itself for this tenant, auto-fill remoteHost
                        // so the tablet bridges MQTT to the Pi without manual config
                        try {
                            val masterInfo = ApiService.getMasterIp()
                            if (masterInfo != null && masterInfo.masterIp.isNotEmpty()) {
                                settings.remoteHost = masterInfo.masterIp
                                settings.remotePort = 1883
                                settings.remoteTlsEnabled = false
                                settings.remoteUseWebSocket = false
                            }
                        } catch (_: Exception) {
                            // No master registered yet — stays on WSS/cloud config. Fine.
                        }

                        onLoginSuccess()
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Authentication failed"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    if (isRegister) "Create Account" else "Sign In",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle register/login
        TextButton(onClick = {
            isRegister = !isRegister
            errorMessage = null
        }) {
            Text(
                if (isRegister) "Already have an account? Sign In"
                else "New here? Create an account",
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Tenant ID hint for existing users
        if (!isRegister) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your tenant ID will be loaded automatically after sign in.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
