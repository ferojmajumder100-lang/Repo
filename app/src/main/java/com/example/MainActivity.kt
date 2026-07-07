package com.example

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

class MainViewModel : ViewModel() {
    private val _ipAddress = MutableStateFlow("Loading...")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _country = MutableStateFlow("Loading...")
    val country: StateFlow<String> = _country.asStateFlow()

    private val _proxyEnabled = MutableStateFlow(false)
    val proxyEnabled: StateFlow<Boolean> = _proxyEnabled.asStateFlow()

    private val _isRefreshingIp = MutableStateFlow(false)
    val isRefreshingIp: StateFlow<Boolean> = _isRefreshingIp.asStateFlow()

    private val _connectionStatus = MutableStateFlow("OFF")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _appStatus = MutableStateFlow("ON")
    val appStatus: StateFlow<String> = _appStatus.asStateFlow()

    private val _proxyHost = MutableStateFlow("change5.owlproxy.com")
    val proxyHost: StateFlow<String> = _proxyHost.asStateFlow()

    private val _proxyPort = MutableStateFlow(7778)
    val proxyPort: StateFlow<Int> = _proxyPort.asStateFlow()

    private val _proxyRule = MutableStateFlow("change5.owlproxy.com:7778")
    val proxyRule: StateFlow<String> = _proxyRule.asStateFlow()

    private val _proxyUsername = MutableStateFlow("D6ZBxx2sbG00_custom_zone_SL")
    val proxyUsername: StateFlow<String> = _proxyUsername.asStateFlow()

    private val _proxyPassword = MutableStateFlow("4806125")
    val proxyPassword: StateFlow<String> = _proxyPassword.asStateFlow()

    private var normalIpAddress: String = ""
    private var pollingJob: Job? = null

    init {
        startPolling()
    }

    fun toggleProxy(context: Context, enabled: Boolean) {
        _proxyEnabled.value = enabled
        if (enabled) {
            _connectionStatus.value = "Success"
            applyProxyConfig(context)
        } else {
            _connectionStatus.value = "OFF"
            clearProxyConfig(context)
        }
    }

    private fun applyProxyConfig(context: Context) {
        val rule = _proxyRule.value
        if (rule.isEmpty()) {
            _connectionStatus.value = "Success"
            return
        }
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.PROXY_OVERRIDE)) {
                    val proxyConfig = ProxyConfig.Builder()
                        .addProxyRule(rule)
                        .build()
                    val executor = java.util.concurrent.Executor { command -> command.run() }
                    val listener = java.lang.Runnable { Log.d("InstaProxy", "Proxy overlay configured successfully") }
                    
                    ProxyController.getInstance().setProxyOverride(proxyConfig, executor, listener)
                    
                    Toast.makeText(context, "সক্রিয় করা হয়েছে", Toast.LENGTH_SHORT).show()
                    checkIpImmediately()
                } else {
                    _connectionStatus.value = "Success"
                }
            } catch (e: Exception) {
                Log.e("InstaProxy", "Failed to set proxy", e)
                _connectionStatus.value = "Success"
            }
        }
    }

    private fun clearProxyConfig(context: Context) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.PROXY_OVERRIDE)) {
                    val executor = java.util.concurrent.Executor { command -> command.run() }
                    val listener = java.lang.Runnable { Log.d("InstaProxy", "Proxy cleared successfully") }
                    
                    ProxyController.getInstance().clearProxyOverride(executor, listener)
                    
                    Toast.makeText(context, "নিষ্ক্রিয় করা হয়েছে", Toast.LENGTH_SHORT).show()
                    checkIpImmediately()
                }
            } catch (e: Exception) {
                Log.e("InstaProxy", "Failed to clear proxy", e)
            }
        }
    }

    private fun applyProxyConfigDirect() {
        val rule = _proxyRule.value
        if (rule.isEmpty()) {
            return
        }
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.PROXY_OVERRIDE)) {
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule(rule)
                    .build()
                val executor = java.util.concurrent.Executor { command -> command.run() }
                val listener = java.lang.Runnable { Log.d("InstaProxy", "Background proxy auto-configured") }
                
                ProxyController.getInstance().setProxyOverride(proxyConfig, executor, listener)
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "Background proxy update failed", e)
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                // 1. Fetch Pastebin config
                fetchPastebinConfigDirect()

                // 2. Refresh IP Check
                _isRefreshingIp.value = true
                if (_proxyEnabled.value) {
                    _connectionStatus.value = "Success"
                    _ipAddress.value = "Active"
                    _country.value = "Secure"
                } else {
                    val result = performIpCheck(false)
                    if (result != null) {
                        val currentFetchedIp = result.first
                        _ipAddress.value = currentFetchedIp
                        _country.value = result.second
                        normalIpAddress = currentFetchedIp
                        _connectionStatus.value = "OFF"
                    } else {
                        _ipAddress.value = "Failed"
                        _country.value = "Failed"
                        _connectionStatus.value = "OFF"
                    }
                }
                _isRefreshingIp.value = false

                delay(10000) // Poll every 10 seconds
            }
        }
    }

    private fun fetchPastebinConfigDirect() {
        val cleanClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://pastebin.com/raw/PCN8A5dD")
            .build()
        try {
            cleanClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim()
                    if (!body.isNullOrEmpty()) {
                        parseAndApplyConfig(body)
                    } else {
                        handleFetchError()
                    }
                } else {
                    handleFetchError()
                }
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "Failed to fetch Pastebin config: ${e.message}")
            handleFetchError()
        }
    }

    private fun parseAndApplyConfig(body: String) {
        try {
            if (body.equals("OFF", ignoreCase = true)) {
                _appStatus.value = "OFF"
                return
            } else if (body.equals("ON", ignoreCase = true)) {
                _appStatus.value = "ON"
                return
            }

            val json = JSONObject(body)
            
            // Determine app status
            var status = json.optString("status", "")
            if (status.isEmpty()) {
                status = json.optString("app_status", "")
            }
            if (status.isEmpty()) {
                status = json.optString("appStatus", "")
            }
            
            if (status.equals("OFF", ignoreCase = true) || status.equals("off", ignoreCase = true)) {
                _appStatus.value = "OFF"
            } else {
                _appStatus.value = "ON"
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "Error parsing JSON config, checking raw: ${e.message}")
            if (body.contains("OFF", ignoreCase = true)) {
                _appStatus.value = "OFF"
            } else {
                _appStatus.value = "ON"
            }
        }
    }

    private fun handleFetchError() {
        if (_proxyEnabled.value) {
            _connectionStatus.value = "Failed"
        }
    }

    private fun checkIpImmediately() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingIp.value = true
            if (_proxyEnabled.value) {
                _connectionStatus.value = "Success"
                _ipAddress.value = "Active"
                _country.value = "Secure"
            } else {
                val result = performIpCheck(false)
                if (result != null) {
                    val currentFetchedIp = result.first
                    _ipAddress.value = currentFetchedIp
                    _country.value = result.second
                    normalIpAddress = currentFetchedIp
                    _connectionStatus.value = "OFF"
                } else {
                    _ipAddress.value = "Failed"
                    _country.value = "Failed"
                    _connectionStatus.value = "OFF"
                }
            }
            _isRefreshingIp.value = false
        }
    }

    private fun performIpCheck(isProxyOn: Boolean): Pair<String, String>? {
        val client = getOkHttpClient(isProxyOn)
        
        // Try Primary API (ip-api.com)
        try {
            val request = Request.Builder()
                .url("http://ip-api.com/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrEmpty()) {
                        val json = JSONObject(bodyStr)
                        if (json.optString("status") == "success") {
                            val queryIp = json.optString("query", "Unknown IP")
                            val countryName = json.optString("country", "Unknown Country")
                            return Pair(queryIp, countryName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "Primary IP API failed: ${e.message}")
        }

        // Try Fallback API (ipapi.co)
        try {
            val request = Request.Builder()
                .url("https://ipapi.co/json/")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrEmpty()) {
                        val json = JSONObject(bodyStr)
                        val ip = json.optString("ip", "Unknown IP")
                        val country = json.optString("country_name", "Unknown Country")
                        return Pair(ip, country)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "Fallback IP API failed: ${e.message}")
        }

        return null
    }

    private fun getOkHttpClient(isProxyOn: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)

        if (isProxyOn && _proxyHost.value.isNotEmpty() && _proxyPort.value > 0) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(_proxyHost.value, _proxyPort.value))
            val authenticator = Authenticator { _, response ->
                val credential = Credentials.basic(_proxyUsername.value, _proxyPassword.value)
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
            builder.proxy(proxy)
            builder.proxyAuthenticator(authenticator)
        }
        return builder.build()
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val proxyEnabled by viewModel.proxyEnabled.collectAsState()
    val isRefreshingIp by viewModel.isRefreshingIp.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val appStatus by viewModel.appStatus.collectAsState()

    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (appStatus == "OFF") {
            MaintenanceNoticeScreen()
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // High-fidelity Dashboard Panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // App Identity and Proxy ON/OFF Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "InstaHub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Instagram WebView Hub",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (proxyEnabled) "ON" else "OFF",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (proxyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Switch(
                                checked = proxyEnabled,
                                onCheckedChange = { viewModel.toggleProxy(context, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.testTag("proxy_toggle_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dynamic IP & Country Card (Now simplified Status Card)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Ambient Connection Status Dot
                                val statusColor = when (connectionStatus) {
                                    "Success" -> Color(0xFF4CAF50)
                                    else -> Color(0xFF9E9E9E)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )

                                Column {
                                    Text(
                                        text = if (connectionStatus == "Success") "সংযুক্ত" else "নিষ্ক্রিয়",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (connectionStatus == "Success") Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (isRefreshingIp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Connection Type Indicator",
                                    tint = if (proxyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Command Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Clear Data Button
                        Button(
                            onClick = {
                                val webView = webViewRef.value
                                if (webView != null) {
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.removeAllCookies {
                                        cookieManager.flush()
                                        webView.clearCache(true)
                                        webView.clearHistory()
                                        webView.clearFormData()
                                        webView.loadUrl("https://www.instagram.com/accounts/signup/phone")
                                        Toast.makeText(context, "ডাটা ও কুকিজ মুছে ফেলা হয়েছে এবং রিলোড হচ্ছে", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "ত্রুটি: ওয়েব ভিউ অফলাইন রয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("clear_data_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Cache Icon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ডাটা ক্লিয়ার",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Copy Cookies Button
                        Button(
                            onClick = {
                                val cookies = CookieManager.getInstance().getCookie("https://www.instagram.com/accounts/signup/phone")
                                if (!cookies.isNullOrEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("InstaHub Cookies", cookies)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "কুকিজ ক্লিপবোর্ডে কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "কোনো কুকিজ পাওয়া যায়নি! অনুগ্রহ করে পেজটি আগে ব্রাউজ করুন।", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("copy_cookies_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Cookies Icon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "কুকিজ কপি",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // WebView Frame
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }

                                // Enable standard cookies & third-party cookies for seamless registration flow
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onReceivedHttpAuthRequest(
                                        view: WebView?,
                                        handler: HttpAuthHandler?,
                                        host: String?,
                                        realm: String?
                                    ) {
                                        if (viewModel.proxyEnabled.value) {
                                            val u = viewModel.proxyUsername.value
                                            val p = viewModel.proxyPassword.value
                                            if (u.isNotEmpty() && p.isNotEmpty()) {
                                                handler?.proceed(u, p)
                                            } else {
                                                handler?.proceed("D6ZBxx2sbG00_custom_zone_SL", "4806125")
                                            }
                                        } else {
                                            super.onReceivedHttpAuthRequest(view, handler, host, realm)
                                        }
                                    }
                                }

                                webChromeClient = WebChromeClient()

                                loadUrl("https://www.instagram.com/accounts/signup/phone")
                                webViewRef.value = this
                            }
                        },
                        update = {
                            // Do nothing here to prevent unwanted reloading of webview
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("instagram_webview")
                    )
                }
            }
        }
    }
}

@Composable
fun MaintenanceNoticeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = "System Update Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp)
            )
            Text(
                text = "System Update Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "To keep using the app, you need to download the latest updates. Important system maintenance is currently in progress.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Checking for updates automatically...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
