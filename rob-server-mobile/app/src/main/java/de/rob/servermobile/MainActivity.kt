package de.rob.servermobile

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val Bg = Color(0xFF080D17)
private val Panel = Color(0xFF152033)
private val Accent = Color(0xFF35B7FF)
private val OnBg = Color(0xFFF5F8FF)

/** One web service on the server. The port is a default only; it is editable in Settings. */
private data class Service(val key: String, val title: String, val icon: ImageVector, val defaultPort: Int)

private val services = listOf(
    Service("cc", "Control Center", Icons.Filled.Dashboard, 8099),
    Service("tt", "TikTok Studio", Icons.Filled.PlayArrow, 8091),
    Service("term", "Terminal", Icons.Filled.Terminal, 8100),
    Service("ad", "AdGen", Icons.Filled.AutoAwesome, 8090),
)

private class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("rob", Context.MODE_PRIVATE)
    var host: String
        get() = sp.getString("host", "") ?: ""
        set(v) = sp.edit().putString("host", v.trim().trimEnd('/')).apply()
    fun port(s: Service) = sp.getInt("port_${s.key}", s.defaultPort)
    fun setPort(s: Service, p: Int) = sp.edit().putInt("port_${s.key}", p).apply()

    /** host may be "http://1.2.3.4" / "https://name" / bare "1.2.3.4" (-> http). */
    fun url(s: Service): String? {
        val h = host
        if (h.isBlank()) return null
        val base = if (h.startsWith("http://") || h.startsWith("https://")) h else "http://$h"
        return "$base:${port(s)}/"
    }
}

class MainActivity : ComponentActivity() {
    private var chooser: ValueCallback<Array<Uri>>? = null
    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        chooser?.onReceiveValue(
            android.webkit.WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
        )
        chooser = null
    }
    private val webViews = mutableMapOf<String, WebView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent, background = Bg, surface = Panel,
                    onSurface = OnBg, onBackground = OnBg, onPrimary = Bg,
                )
            ) { RobApp(prefs) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun webViewFor(s: Service, onState: (loading: Boolean, error: String?) -> Unit): WebView =
        webViews.getOrPut(s.key) {
            WebView(this).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mediaPlaybackRequiresUserGesture = false
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView, url: String?) = onState(false, null)
                    override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                        if (r.isForMainFrame) onState(false, "${e.description} (${r.url.host}:${r.url.port})")
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams
                    ): Boolean {
                        chooser?.onReceiveValue(null)
                        chooser = cb
                        return try { pickFile.launch(p.createIntent()); true }
                        catch (e: Exception) { chooser = null; false }
                    }
                }
            }
        }

    @Composable
    private fun RobApp(prefs: Prefs) {
        var tab by remember { mutableIntStateOf(0) } // 0..services.size-1, services.size = settings
        var host by remember { mutableStateOf(prefs.host) }
        val settingsIdx = services.size
        if (host.isBlank()) tab = settingsIdx

        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    services.forEachIndexed { i, s ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(s.icon, s.title) },
                            label = { Text(s.title, maxLines = 1) },
                        )
                    }
                    NavigationBarItem(
                        selected = tab == settingsIdx, onClick = { tab = settingsIdx },
                        icon = { Icon(Icons.Filled.Settings, null) },
                        label = { Text(stringResource(R.string.settings), maxLines = 1) },
                    )
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().background(Bg)) {
                if (tab == settingsIdx) SettingsPage(prefs, host) { host = it; if (it.isNotBlank()) tab = 0 }
                else WebPage(services[tab], prefs)
            }
        }
    }

    @Composable
    private fun WebPage(s: Service, prefs: Prefs) {
        val url = prefs.url(s)
        var loading by remember(s.key) { mutableStateOf(true) }
        var error by remember(s.key) { mutableStateOf<String?>(null) }
        if (url == null) { Text(stringResource(R.string.no_server), Modifier.padding(16.dp)); return }
        val wv = remember(s.key, url) {
            webViewFor(s) { l, e -> loading = l; error = e }.also {
                if (it.url == null) { loading = true; it.loadUrl(url) }
            }
        }
        BackHandler(enabled = wv.canGoBack()) { wv.goBack() }
        Column(Modifier.fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                Row(Modifier.fillMaxWidth().background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠ $it", Modifier.weight(1f))
                    Button(onClick = { error = null; loading = true; wv.loadUrl(url) }) { Text(stringResource(R.string.reload)) }
                }
            }
            AndroidView(factory = {
                (wv.parent as? ViewGroup)?.removeView(wv); wv
            }, modifier = Modifier.fillMaxSize())
        }
    }

    @Composable
    private fun SettingsPage(prefs: Prefs, host: String, onSaved: (String) -> Unit) {
        var h by remember { mutableStateOf(host) }
        val ports = remember { mutableStateMapOf<String, String>().apply { services.forEach { put(it.key, prefs.port(it).toString()) } } }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                h, { h = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.server_label)) },
            )
            Text(stringResource(R.string.ports_note))
            services.forEach { s ->
                OutlinedTextField(
                    ports[s.key] ?: "", { ports[s.key] = it.filter(Char::isDigit).take(5) },
                    Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.title) },
                )
            }
            Button(
                onClick = {
                    prefs.host = h
                    services.forEach { s -> ports[s.key]?.toIntOrNull()?.let { prefs.setPort(s, it) } }
                    webViews.values.forEach { it.destroy() }
                    webViews.clear()
                    onSaved(prefs.host)
                },
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
