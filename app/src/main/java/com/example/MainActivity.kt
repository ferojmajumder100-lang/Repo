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
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    // OTP and Live Ranges System states
    private val _liveRanges = MutableStateFlow<List<String>>(emptyList())
    val liveRanges: StateFlow<List<String>> = _liveRanges.asStateFlow()

    private val _rangeFetchError = MutableStateFlow(false)
    val rangeFetchError: StateFlow<Boolean> = _rangeFetchError.asStateFlow()

    private val _currentNumber = MutableStateFlow<String?>(null)
    val currentNumber: StateFlow<String?> = _currentNumber.asStateFlow()

    private val _currentOtp = MutableStateFlow<String?>(null)
    val currentOtp: StateFlow<String?> = _currentOtp.asStateFlow()

    private val _isFetchingNumber = MutableStateFlow(false)
    val isFetchingNumber: StateFlow<Boolean> = _isFetchingNumber.asStateFlow()

    private val _otpStatus = MutableStateFlow("No active number")
    val otpStatus: StateFlow<String> = _otpStatus.asStateFlow()

    private var rangePollingJob: Job? = null
    private var otpPollingJob: Job? = null

    init {
        startPolling()
        startRangePolling()
    }

    fun toggleProxy(context: Context, enabled: Boolean) {
        _proxyEnabled.value = enabled
        if (enabled) {
            _connectionStatus.value = "Success"
            _ipAddress.value = "Checking..."
            _country.value = "Checking..."
            applyProxyConfig(context)
        } else {
            _connectionStatus.value = "OFF"
            _ipAddress.value = "Checking..."
            _country.value = "Checking..."
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
                val isProxy = _proxyEnabled.value
                val result = performIpCheck(isProxy)
                if (result != null) {
                    _ipAddress.value = result.first
                    _country.value = result.second
                    if (!isProxy) {
                        normalIpAddress = result.first
                        _connectionStatus.value = "OFF"
                    } else {
                        _connectionStatus.value = "Success"
                    }
                } else {
                    if (isProxy) {
                        if (_ipAddress.value == "Checking..." || _ipAddress.value == "Loading...") {
                            _ipAddress.value = "Active"
                            _country.value = "Secure"
                        }
                        _connectionStatus.value = "Success"
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
        Log.e("InstaProxy", "Pastebin config fetch failed, keeping current state.")
    }

    private fun checkIpImmediately() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingIp.value = true
            val isProxy = _proxyEnabled.value
            val result = performIpCheck(isProxy)
            if (result != null) {
                _ipAddress.value = result.first
                _country.value = result.second
                if (!isProxy) {
                    normalIpAddress = result.first
                    _connectionStatus.value = "OFF"
                } else {
                    _connectionStatus.value = "Success"
                }
            } else {
                if (isProxy) {
                    if (_ipAddress.value == "Checking..." || _ipAddress.value == "Loading...") {
                        _ipAddress.value = "Active"
                        _country.value = "Secure"
                    }
                    _connectionStatus.value = "Success"
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
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val credential = if (isProxyOn) {
            Credentials.basic(_proxyUsername.value, _proxyPassword.value)
        } else null
        
        // 1. Try Primary API (https://ipwho.is/)
        try {
            val reqBuilder = Request.Builder()
                .url("https://ipwho.is/")
                .header("User-Agent", userAgent)
            if (credential != null) {
                reqBuilder.header("Proxy-Authorization", credential)
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrEmpty()) {
                        val json = JSONObject(bodyStr)
                        if (json.optBoolean("success", false)) {
                            val ip = json.optString("ip", "Unknown IP")
                            val country = json.optString("country", "Unknown Country")
                            Log.d("InstaProxy", "Fetched IP: $ip, Country: $country from ipwho.is")
                            return Pair(ip, country)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "ipwho.is failed: ${e.message}")
        }

        // 2. Try Fallback API (https://ipapi.co/json/)
        try {
            val reqBuilder = Request.Builder()
                .url("https://ipapi.co/json/")
                .header("User-Agent", userAgent)
            if (credential != null) {
                reqBuilder.header("Proxy-Authorization", credential)
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrEmpty()) {
                        val json = JSONObject(bodyStr)
                        val ip = json.optString("ip", "")
                        if (ip.isNotEmpty()) {
                            val country = json.optString("country_name", "Unknown Country")
                            Log.d("InstaProxy", "Fetched IP: $ip, Country: $country from ipapi.co")
                            return Pair(ip, country)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "ipapi.co failed: ${e.message}")
        }

        // 3. Try Fallback 2 (https://ipinfo.io/json)
        try {
            val reqBuilder = Request.Builder()
                .url("https://ipinfo.io/json")
                .header("User-Agent", userAgent)
            if (credential != null) {
                reqBuilder.header("Proxy-Authorization", credential)
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrEmpty()) {
                        val json = JSONObject(bodyStr)
                        val ip = json.optString("ip", "")
                        if (ip.isNotEmpty()) {
                            val countryCode = json.optString("country", "")
                            val country = if (countryCode.isNotEmpty()) {
                                java.util.Locale("", countryCode).displayCountry
                            } else {
                                "Unknown Country"
                            }
                            Log.d("InstaProxy", "Fetched IP: $ip, Country: $country from ipinfo.io")
                            return Pair(ip, country)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "ipinfo.io failed: ${e.message}")
        }

        // 4. Try Fallback 3 (http://ip-api.com/json) (Cleartext might be blocked, but try as fallback)
        try {
            val reqBuilder = Request.Builder()
                .url("http://ip-api.com/json")
                .header("User-Agent", userAgent)
            if (credential != null) {
                reqBuilder.header("Proxy-Authorization", credential)
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
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

        // 5. Try Fallback 4 (api.ipify.org) to at least get the IP
        try {
            val reqBuilder = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .header("User-Agent", userAgent)
            if (credential != null) {
                reqBuilder.header("Proxy-Authorization", credential)
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrEmpty()) {
                        val json = JSONObject(bodyStr)
                        val ip = json.optString("ip", "Unknown IP")
                        return Pair(ip, "Secure")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "Second fallback IP API failed: ${e.message}")
        }

        return null
    }

    private fun getOkHttpClient(isProxyOn: Boolean): OkHttpClient {
        val timeout = if (isProxyOn) 12L else 5L
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)

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

    private fun startRangePolling() {
        rangePollingJob?.cancel()
        rangePollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                fetchLiveRangesDirect()
                delay(5000) // Poll every 5 seconds
            }
        }
    }

    private fun fetchLiveRangesDirect() {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://api.2oo9.cloud/MXS47FLFX0U/tnevs/@public/api/liveaccess")
            .header("mauthapi", "MX1RN9ZKIHY")
            .header("Content-Type", "application/json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val dataObj = json.optJSONObject("data")
                        val servicesArr = dataObj?.optJSONArray("services")
                        if (servicesArr != null) {
                            var foundRanges = emptyList<String>()
                            for (i in 0 until servicesArr.length()) {
                                val service = servicesArr.getJSONObject(i)
                                val sid = service.optString("sid", "")
                                if (sid.equals("FACEBOOK", ignoreCase = true)) {
                                    val rangesArr = service.optJSONArray("ranges")
                                    if (rangesArr != null) {
                                        val list = mutableListOf<String>()
                                        for (j in 0 until rangesArr.length()) {
                                            list.add(rangesArr.getString(j))
                                        }
                                        foundRanges = list
                                    }
                                    break
                                }
                            }
                            // Take last 5 ranges
                            _liveRanges.value = foundRanges.takeLast(5)
                            _rangeFetchError.value = foundRanges.isEmpty()
                        } else {
                            _liveRanges.value = emptyList()
                            _rangeFetchError.value = true
                        }
                    } else {
                        _liveRanges.value = emptyList()
                        _rangeFetchError.value = true
                    }
                } else {
                    _liveRanges.value = emptyList()
                    _rangeFetchError.value = true
                }
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "Failed to fetch live ranges: ${e.message}")
            if (_liveRanges.value.isEmpty()) {
                _rangeFetchError.value = true
            }
        }
    }

    fun fetchNumberForRange(context: Context, rangeCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isFetchingNumber.value = true
            _currentOtp.value = null
            _otpStatus.value = "Fetching number..."
            
            // Clean the range code (remove 'X's)
            val rid = rangeCode.replace("X", "", ignoreCase = true).trim()
            
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
                
            val jsonBody = JSONObject().put("rid", rid).toString()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = jsonBody.toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("https://api.2oo9.cloud/MXS47FLFX0U/tnevs/@public/api/getnum")
                .header("mauthapi", "MX1RN9ZKIHY")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()
                
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val dataObj = json.optJSONObject("data")
                            val fullNumber = dataObj?.optString("full_number", "") ?: ""
                            val noPlusNumber = dataObj?.optString("no_plus_number", "") ?: ""
                            
                            val finalNumber = if (fullNumber.isNotEmpty()) {
                                fullNumber.replace("+", "").trim()
                            } else if (noPlusNumber.isNotEmpty()) {
                                noPlusNumber.replace("+", "").trim()
                            } else {
                                ""
                            }
                            
                            if (finalNumber.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    _currentNumber.value = finalNumber
                                    _otpStatus.value = "Waiting for OTP..."
                                    
                                    // Auto-copy to clipboard
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Fetched Number", finalNumber)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Number copied: $finalNumber", Toast.LENGTH_SHORT).show()
                                }
                                
                                // Start polling for OTP for this number
                                startOtpPolling(context, finalNumber)
                            } else {
                                withContext(Dispatchers.Main) {
                                    _otpStatus.value = "No numbers available"
                                    Toast.makeText(context, "No numbers available for this range", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                _otpStatus.value = "Error fetching number"
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _otpStatus.value = "Server error"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("InstaProxy", "Error fetching number: ${e.message}")
                withContext(Dispatchers.Main) {
                    _otpStatus.value = "Network failed"
                    Toast.makeText(context, "Failed to connect: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isFetchingNumber.value = false
            }
        }
    }

    private fun startOtpPolling(context: Context, phone: String) {
        otpPollingJob?.cancel()
        otpPollingJob = viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
                
            val request = Request.Builder()
                .url("https://api.2oo9.cloud/MXS47FLFX0U/tnevs/@public/api/success-otp")
                .header("mauthapi", "MX1RN9ZKIHY")
                .header("Content-Type", "application/json")
                .build()
                
            var attempts = 0
            while (isActive && attempts < 150) { // Limit to 5 minutes (150 * 2s)
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrEmpty()) {
                                val json = JSONObject(body)
                                val dataObj = json.optJSONObject("data")
                                val otpsArr = dataObj?.optJSONArray("otps")
                                if (otpsArr != null) {
                                    var foundOtp: String? = null
                                    for (i in 0 until otpsArr.length()) {
                                        val otpItem = otpsArr.getJSONObject(i)
                                        val otpNumber = otpItem.optString("number", "").replace("+", "").trim()
                                        if (otpNumber == phone) {
                                            val message = otpItem.optString("message", "")
                                            val extracted = extractOtpFromMessage(message)
                                            if (extracted != "N/A") {
                                                foundOtp = extracted
                                                break
                                            }
                                        }
                                    }
                                    
                                    if (foundOtp != null) {
                                        val finalOtp = foundOtp
                                        withContext(Dispatchers.Main) {
                                            _currentOtp.value = finalOtp
                                            _otpStatus.value = "OTP Received: $finalOtp"
                                            saveOtpToHistory(context, phone, finalOtp)
                                            
                                            // Auto-copy OTP to clipboard
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("OTP Code", finalOtp)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "OTP Code Copied: $finalOtp", Toast.LENGTH_SHORT).show()
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("InstaProxy", "Error polling OTP: ${e.message}")
                }
                delay(2000) // check every 2 seconds
                attempts++
            }
            
            if (attempts >= 150 && _currentOtp.value == null) {
                withContext(Dispatchers.Main) {
                    _otpStatus.value = "OTP Timeout"
                }
            }
        }
    }

    private fun extractOtpFromMessage(message: String): String {
        // Try to match "FB-XXXXX" or "FB-XXXXXX"
        val fbRegex = Regex("FB-(\\d{5,6})")
        fbRegex.find(message)?.groupValues?.getOrNull(1)?.let { return it }

        // General 5 to 6 digit finder
        val digitRegex = Regex("\\b(\\d{5,6})\\b")
        digitRegex.find(message)?.value?.let { return it }

        // General 3 to 8 digit finder as fallback
        val fallbackRegex = Regex("\\b(\\d{3,8})\\b")
        fallbackRegex.find(message)?.value?.let { return it }

        return "N/A"
    }

    fun saveOtpToHistory(context: Context, phone: String, otp: String) {
        val sharedPrefs = context.getSharedPreferences("otp_prefs", Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val key = "otp_history_$today"
        val existingJson = sharedPrefs.getString(key, "[]") ?: "[]"
        
        try {
            val array = org.json.JSONArray(existingJson)
            var exists = false
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item.optString("phone") == phone && item.optString("otp") == otp) {
                    exists = true
                    break
                }
            }
            
            if (!exists) {
                val item = org.json.JSONObject()
                item.put("phone", phone)
                item.put("otp", otp)
                item.put("time", java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
                array.put(item)
                sharedPrefs.edit().putString(key, array.toString()).apply()
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "Failed to save OTP history: ${e.message}")
        }
    }

    fun getOtpHistory(context: Context): List<OtpHistoryItem> {
        val sharedPrefs = context.getSharedPreferences("otp_prefs", Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val key = "otp_history_$today"
        val jsonStr = sharedPrefs.getString(key, "[]") ?: "[]"
        
        val list = mutableListOf<OtpHistoryItem>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    OtpHistoryItem(
                        phone = item.optString("phone", ""),
                        otp = item.optString("otp", ""),
                        timestamp = item.optString("time", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("InstaProxy", "Failed to get OTP history: ${e.message}")
        }
        return list.reversed()
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        rangePollingJob?.cancel()
        otpPollingJob?.cancel()
    }
}

data class OtpHistoryItem(
    val phone: String,
    val otp: String,
    val timestamp: String
)

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
    val ipAddress by viewModel.ipAddress.collectAsState()
    val country by viewModel.country.collectAsState()

    val liveRanges by viewModel.liveRanges.collectAsState()
    val rangeFetchError by viewModel.rangeFetchError.collectAsState()
    val currentNumber by viewModel.currentNumber.collectAsState()
    val currentOtp by viewModel.currentOtp.collectAsState()
    val isFetchingNumber by viewModel.isFetchingNumber.collectAsState()
    val otpStatus by viewModel.otpStatus.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            InstagramLogo(size = 36.dp)
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live Range Instagram Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        InstagramLogo(size = 18.dp)
                        Text(
                            text = "Live Range Instagram",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    if (liveRanges.isEmpty()) {
                        Text(
                            text = "No range",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            liveRanges.forEach { range ->
                                AssistChip(
                                    onClick = { viewModel.fetchNumberForRange(context, range) },
                                    label = { 
                                        Text(
                                            text = range,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dynamic IP, Connection & OTP Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            // Row 1: Connection Status & Refresh/Fetching Indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                            text = if (connectionStatus == "Success") "Connect" else "নিষ্ক্রিয়",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (connectionStatus == "Success") Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                if (isRefreshingIp || isFetchingNumber) {
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

                            // OTP & Active Number Info
                            if (currentNumber != null || isFetchingNumber || otpStatus != "No active number") {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Spacer(
                                    modifier = Modifier
                                        .height(1.dp)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        Text(
                                            text = "Active Number",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = currentNumber ?: "Fetching...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (currentNumber != null) {
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val clip = ClipData.newPlainText("Active Number", currentNumber)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copy number",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.weight(1.2f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "OTP Status",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        if (currentOtp != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = currentOtp ?: "",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF4CAF50),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val clip = ClipData.newPlainText("OTP Code", currentOtp)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copy OTP",
                                                        tint = Color(0xFF4CAF50),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = otpStatus,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (otpStatus.contains("Waiting")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Command Buttons Row (Clear Data, Copy Cookies, OTP History)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Cache Icon",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ক্লিয়ার",
                                fontSize = 11.sp,
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
                                    Toast.makeText(context, "কুকিজ কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "কোনো কুকিজ পাওয়া যায়নি!", Toast.LENGTH_LONG).show()
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
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Cookies Icon",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "কুকিজ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // OTP History Button
                        Button(
                            onClick = {
                                showHistoryDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(42.dp)
                                .testTag("otp_history_button"),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History Icon",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Otp History",
                                fontSize = 11.sp,
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

    // Custom Otp History Dialog
    if (showHistoryDialog) {
        val historyList = remember { viewModel.getOtpHistory(context) }
        
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "আজকের ওটিপি ইতিহাস",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "আজ কোনো ওটিপি পাওয়া যায়নি",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(historyList.size) { index ->
                            val item = historyList[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = item.phone,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = item.timestamp,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = item.otp,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF4CAF50)
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("History OTP", item.otp)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("বন্ধ করুন")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
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

@Composable
fun InstagramLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val outerSquareSize = size * 20f / 36f
    val innerCircleSize = size * 8f / 36f
    val dotSize = size * 2f / 36f
    val borderWidth = size * 2f / 36f
    val dotOffset = size * 2.5f / 36f
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFCAF45), // Yellow
                        Color(0xFFF77737), // Orange
                        Color(0xFFE1306C), // Pink-Red
                        Color(0xFFC13584), // Purple-Pink
                        Color(0xFF833AB4), // Purple
                        Color(0xFF405DE6)  // Blue
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 100f),
                    end = androidx.compose.ui.geometry.Offset(100f, 0f)
                ),
                shape = RoundedCornerShape(size * 8f / 36f)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer white contour (rounded square)
        Box(
            modifier = Modifier
                .size(outerSquareSize)
                .border(
                    width = borderWidth,
                    color = Color.White,
                    shape = RoundedCornerShape(size * 6f / 36f)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner circle
            Box(
                modifier = Modifier
                    .size(innerCircleSize)
                    .border(
                        width = borderWidth,
                        color = Color.White,
                        shape = CircleShape
                    )
            )
            
            // Top-right dot
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .background(Color.White, shape = CircleShape)
                    .align(Alignment.TopEnd)
                    .offset(x = -dotOffset, y = dotOffset)
            )
        }
    }
}

