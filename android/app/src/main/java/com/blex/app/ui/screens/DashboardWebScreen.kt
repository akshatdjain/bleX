package com.blex.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.blex.app.data.SettingsManager

/**
 * DashboardWebScreen — Loads the BleX Web UI inside a full-screen WebView.
 *
 * URL is derived from the existing `apiBaseUrl` setting in SettingsManager:
 *   e.g. http://192.168.1.100:8001  →  opens that URL directly
 *
 * If apiBaseUrl is empty, shows a helpful "not configured" placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DashboardWebScreen() {
    val context = LocalContext.current
    val appSettings = remember { SettingsManager.getInstance(context) }

    // Build the URL to load — use webDashboardUrl specifically
    val rawUrl = appSettings.webDashboardUrl.trim()
    val url = when {
        rawUrl.isEmpty() -> null
        rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
        else -> "http://$rawUrl"
    }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Secure WebView auto-login via one-time nonce:
    // 1. App calls POST /api/auth/web-nonce (sends blex_token cookie) → gets 32-byte nonce
    // 2. WebView loads /asset/api/auth/weblogin?nonce=XXX
    // 3. Server validates nonce, deletes it (one-time use), sets httpOnly cookie, redirects to /blex/dashboard
    // Nonce expires in 60s — even if URL is logged, it's already consumed.
    var webLoginUrl by remember { mutableStateOf<String?>(null) }
    var nonceError by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url != null) {
            val token = appSettings.authToken
            if (token.isBlank()) {
                // No token stored — go straight to dashboard (will show landing page)
                webLoginUrl = url
                return@LaunchedEffect
            }
            try {
                val baseUrl = appSettings.apiBaseUrl.trimEnd('/')
                val nonceUrl = "$baseUrl/api/auth/web-nonce"
                android.util.Log.d("BleX.WebDash", "Fetching nonce from $nonceUrl")

                val nonce = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val conn = java.net.URL(nonceUrl).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Cookie", "blex_token=$token")
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val code = conn.responseCode
                    android.util.Log.d("BleX.WebDash", "Nonce response code: $code")
                    if (code == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        android.util.Log.d("BleX.WebDash", "Nonce body: $body")
                        org.json.JSONObject(body).getString("nonce")
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        android.util.Log.e("BleX.WebDash", "Nonce error $code: $err")
                        null
                    }
                }

                webLoginUrl = if (nonce != null) {
                    "$baseUrl/api/auth/weblogin?nonce=$nonce".also {
                        android.util.Log.d("BleX.WebDash", "WebView loading: $it")
                    }
                } else {
                    url // fallback
                }
            } catch (e: Exception) {
                android.util.Log.e("BleX.WebDash", "Nonce fetch failed: ${e.message}")
                webLoginUrl = url
            }
        }
    }

    if (url == null) {
        // ... (unconfigured state)
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
                    "Web Dashboard URL not configured",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Go to Settings → Remote Server → Web Dashboard URL and enter your server address " +
                    "(e.g. http://93.127.206.7:9000) to load the UI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Show spinner while fetching nonce
    if (webLoginUrl == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
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
                    webViewRef?.reload()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        } else {
            // WebView wrapped in native SwipeRefreshLayout for flawless swipe-to-refresh
            AndroidView(
                factory = { ctx ->
                    androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx).apply {
                        setOnRefreshListener {
                            isRefreshing = true
                            webViewRef?.reload()
                        }
                        
                        val webView = WebView(ctx).apply {
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
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    // Don't show overlay spinner if we are already showing pull-to-refresh
                                    if (!isRefreshing) isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url2: String?) {
                                    isLoading = false
                                    isRefreshing = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    // Only trigger error UI for the main frame
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        isRefreshing = false
                                        hasError = true
                                        errorMessage = error?.description?.toString() ?: ""
                                    }
                                }
                            }
                            loadUrl(webLoginUrl!!)
                        }
                        webViewRef = webView
                        addView(webView)
                    }
                },
                update = { swipeLayout ->
                    swipeLayout.isRefreshing = isRefreshing
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading spinner overlay (only for initial load, not for pull-to-refresh)
        AnimatedVisibility(
            visible = isLoading && !hasError && !isRefreshing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Loading dashboard...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
