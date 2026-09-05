package de.spardirekt.ugcagent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import de.spardirekt.ugcagent.bridge.NativeBridge
import de.spardirekt.ugcagent.data.HistoryStore
import de.spardirekt.ugcagent.data.ImageStore
import de.spardirekt.ugcagent.data.SecureStore
import de.spardirekt.ugcagent.openai.OpenAiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: NativeBridge
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val imageStore by lazy { ImageStore(applicationContext) }
    private val secureStore by lazy { SecureStore(applicationContext) }
    private val historyStore by lazy { HistoryStore(applicationContext) }

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ImageStore.MAX_IMAGES),
    ) { uris ->
        if (uris.isNullOrEmpty()) {
            if (::bridge.isInitialized) bridge.onPickCancelled()
            return@registerForActivityResult
        }
        importUris(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0A")
        window.navigationBarColor = Color.parseColor("#0A0A0A")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        webView = WebView(this)
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.databaseEnabled = true

        bridge = NativeBridge(
            webView = webView,
            scope = scope,
            secureStore = secureStore,
            historyStore = historyStore,
            imageStore = imageStore,
            openAi = OpenAiClient(),
            onPickImages = {
                pickImages.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onCopy = { text -> copyToClipboard(text) },
        )
        webView.addJavascriptInterface(bridge, "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = params.createIntent().apply {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    type = "image/*"
                }
                return try {
                    startActivityForResult(Intent.createChooser(intent, "Fotos"), FILE_CHOOSER)
                    true
                } catch (_: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("WebView file chooser fallback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_CHOOSER) return
        val uris = mutableListOf<Uri>()
        if (resultCode == RESULT_OK && data != null) {
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { uris += it }
                }
            } else {
                data.data?.let { uris += it }
            }
        }
        filePathCallback?.onReceiveValue(uris.toTypedArray().ifEmpty { null })
        filePathCallback = null
        if (uris.isNotEmpty()) importUris(uris)
    }

    private fun importUris(uris: List<Uri>) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { imageStore.importAll(uris) }
            }.onSuccess {
                bridge.onImagesImported()
            }.onFailure {
                Toast.makeText(this@MainActivity, "Bilder konnten nicht gelesen werden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("UGC Prompt", text))
        Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        scope.cancel()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val FILE_CHOOSER = 91
    }
}
