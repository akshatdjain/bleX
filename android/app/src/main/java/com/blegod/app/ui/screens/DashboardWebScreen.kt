package com.blegod.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.blegod.app.data.SettingsManager

/**
 * DashboardWebScreen — Loads the BleX Web UI inside a full-screen WebView.
 *
 * URL is derived from the existing `apiBaseUrl` setting in SettingsManager:
 *   e.g. http://192.168.1.100:8001  →  opens that URL directly
 *
 * If apiBaseUrl is empty, shows a helpful "not configured" placeholder.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DashboardWebScreen() {
    val context = LocalContext.current
    val appSettings = remember { SettingsManager.getInstance(context) }

    // Build the URL to load — use apiBaseUrl directly (it already includes host + port)
    val rawUrl = appSettings.apiBaseUrl.trim()
    val url = when {
        rawUrl.isEmpty() -> null
        rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
        else -> "http://$rawUrl"
    }

    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    if (url == null) {
        // API URL not configured yet — show setup instructions
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Dashboard URL not configured",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Go to Settings → API Base URL and enter your server address " +
                    "(e.g. http://192.168.1.100:8001) to load the web dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasError) {
            // Error state with retry button
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Cannot reach dashboard",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    errorMessage.ifEmpty { "Check that the BleX UI API is running at:\n$url" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    hasError = false
                    isLoading = true
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        } else {
            // WebView
            var webViewRef by remember { mutableStateOf<WebView?>(null) }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        this.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = false
                            displayZoomControls = false
                            // Allow mixed content (HTTP assets from HTTPS page, common on LAN)
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url2: String?) {
                                isLoading = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                // Only trigger error UI for the main frame
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    hasError = true
                                    errorMessage = error?.description?.toString() ?: ""
                                }
                            }
                        }
                        loadUrl(url)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    // Re-load if URL changed (e.g. user updated settings and came back)
                    webViewRef = webView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading spinner overlay
        AnimatedVisibility(
            visible = isLoading && !hasError,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator()
        }
    }
}
