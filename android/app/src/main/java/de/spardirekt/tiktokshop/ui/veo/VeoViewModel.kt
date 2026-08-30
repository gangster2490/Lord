package de.spardirekt.tiktokshop.ui.veo

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.tiktokshop.TikTokShopApplication
import de.spardirekt.tiktokshop.data.EncodedImage
import de.spardirekt.tiktokshop.data.ImageEncoder
import de.spardirekt.tiktokshop.data.ProductDna
import de.spardirekt.tiktokshop.data.ResultFormatter
import de.spardirekt.tiktokshop.data.VeoHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VeoPage { Home, Upload, Result, History, Settings }

data class VeoUiState(
    val page: VeoPage = VeoPage.Home,
    val apiKey: String = "",
    val analysisModel: String = "gpt-4o",
    val imageModel: String = "dall-e-3",
    val photos: List<EncodedImage> = emptyList(),
    val dna: ProductDna? = null,
    val prompt: String = "",
    val resultUrl: String = "",
    val history: List<VeoHistoryEntry> = emptyList(),
    val loading: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val toast: String? = null,
    val keyTest: String? = null,
    val keyTestOk: Boolean? = null,
)

class VeoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TikTokShopApplication
    private val _state = MutableStateFlow(VeoUiState())
    val state: StateFlow<VeoUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            app.preferences.snapshot.collect { prefs ->
                _state.update {
                    it.copy(
                        apiKey = if (it.apiKey.isBlank()) prefs.openAiKey else it.apiKey,
                        analysisModel = prefs.analysisModel,
                        imageModel = prefs.imageModel,
                        history = prefs.veoHistory,
                    )
                }
            }
        }
    }

    fun show(page: VeoPage) {
        _state.update { it.copy(page = page, error = null) }
    }

    fun startNewProduct() {
        _state.update {
            it.copy(
                page = VeoPage.Upload,
                photos = emptyList(),
                dna = null,
                prompt = "",
                resultUrl = "",
                error = null,
                status = null,
                loading = false,
            )
        }
    }

    fun onApiKeyChange(value: String) {
        _state.update { it.copy(apiKey = value) }
        viewModelScope.launch { app.preferences.setOpenAiKey(value) }
    }

    fun saveSettings() {
        val s = _state.value
        viewModelScope.launch {
            app.preferences.setOpenAiKey(s.apiKey.trim())
            app.preferences.setAnalysisModel(s.analysisModel)
            app.preferences.setImageModel(s.imageModel)
            _state.update { it.copy(toast = "✅ Gespeichert!") }
        }
    }

    fun onAnalysisModel(value: String) {
        _state.update { it.copy(analysisModel = value) }
        viewModelScope.launch { app.preferences.setAnalysisModel(value) }
    }

    fun onImageModel(value: String) {
        _state.update { it.copy(imageModel = value) }
        viewModelScope.launch { app.preferences.setImageModel(value) }
    }

    fun testKey() {
        val key = _state.value.apiKey.trim()
        if (key.isEmpty()) {
            _state.update { it.copy(keyTest = "Kein Key eingegeben", keyTestOk = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(keyTest = "Teste...", keyTestOk = null) }
            try {
                withContext(Dispatchers.IO) { app.openAiApi.testKey(key) }
                _state.update { it.copy(keyTest = "✅ Key funktioniert!", keyTestOk = true) }
            } catch (e: Exception) {
                _state.update { it.copy(keyTest = "❌ ${e.message}", keyTestOk = false) }
            }
        }
    }

    fun addPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            val remaining = 9 - _state.value.photos.size
            val toAdd = uris.take(remaining)
            val encoded = withContext(Dispatchers.IO) {
                toAdd.mapNotNull { uri ->
                    runCatching {
                        ImageEncoder.encode(getApplication<Application>().contentResolver, uri)
                    }.getOrNull()
                }
            }
            _state.update { it.copy(photos = it.photos + encoded, error = null) }
        }
    }

    fun removePhoto(index: Int) {
        _state.update { current ->
            val next = current.photos.toMutableList().also { it.removeAt(index) }
            current.copy(photos = next, dna = if (next.isEmpty()) null else current.dna)
        }
    }

    fun analyze() {
        val s = _state.value
        if (s.apiKey.isBlank()) {
            _state.update { it.copy(toast = "⚠️ Bitte zuerst API Key eingeben", page = VeoPage.Settings) }
            return
        }
        if (s.photos.isEmpty()) {
            _state.update { it.copy(toast = "Bitte erst Fotos hochladen") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, status = "🔍 Analysiere Produkt mit GPT-4o...", error = null) }
            try {
                val dna = withContext(Dispatchers.IO) {
                    app.openAiApi.analyzeProduct(
                        apiKey = s.apiKey,
                        model = s.analysisModel,
                        dataUrls = s.photos.map { ImageEncoder.toDataUrl(it) },
                    )
                }
                _state.update {
                    it.copy(loading = false, status = null, dna = dna, toast = "✅ Analyse abgeschlossen!")
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, status = null, error = e.message) }
            }
        }
    }

    fun generateImage() {
        val s = _state.value
        if (s.apiKey.isBlank()) {
            _state.update { it.copy(toast = "⚠️ Bitte zuerst API Key eingeben", page = VeoPage.Settings) }
            return
        }
        if (s.photos.isEmpty()) {
            _state.update { it.copy(toast = "Bitte erst Fotos hochladen") }
            return
        }
        viewModelScope.launch {
            var dna = s.dna
            if (dna == null) {
                _state.update { it.copy(loading = true, status = "🔍 Analysiere Produkt mit GPT-4o...") }
                try {
                    dna = withContext(Dispatchers.IO) {
                        app.openAiApi.analyzeProduct(
                            apiKey = s.apiKey,
                            model = s.analysisModel,
                            dataUrls = s.photos.map { ImageEncoder.toDataUrl(it) },
                        )
                    }
                    _state.update { it.copy(dna = dna) }
                } catch (e: Exception) {
                    _state.update { it.copy(loading = false, status = null, error = e.message) }
                    return@launch
                }
            }
            _state.update { it.copy(loading = true, status = "🎨 Generiere 9:16 VEO Bild...") }
            val prompt = ResultFormatter.buildVeoReferencePrompt(dna)
            try {
                val url = withContext(Dispatchers.IO) {
                    app.openAiApi.generateImage(s.apiKey, s.imageModel, prompt)
                }
                val entry = VeoHistoryEntry(
                    id = System.currentTimeMillis(),
                    date = SimpleDateFormat("dd.MM.yyyy, HH:mm:ss", Locale.GERMANY).format(Date()),
                    productName = dna?.name?.ifBlank { "Unbekannt" } ?: "Unbekannt",
                    prompt = prompt,
                    resultUrl = url,
                    dna = dna,
                )
                val history = (listOf(entry) + s.history).take(20)
                app.preferences.setHistory(history)
                _state.update {
                    it.copy(
                        loading = false,
                        status = null,
                        prompt = prompt,
                        resultUrl = url,
                        history = history,
                        page = VeoPage.Result,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, status = null, error = e.message) }
            }
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    fun loadHistoryResult(entry: VeoHistoryEntry) {
        _state.update {
            it.copy(
                resultUrl = entry.resultUrl,
                prompt = entry.prompt,
                dna = entry.dna,
                page = VeoPage.Result,
            )
        }
    }

    fun loadHistoryProduct(entry: VeoHistoryEntry) {
        _state.update {
            it.copy(
                dna = entry.dna,
                prompt = entry.prompt,
                page = VeoPage.Upload,
                toast = "📦 Produkt geladen — bereit für neue Generation",
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            app.preferences.clearHistory()
            _state.update { it.copy(history = emptyList(), toast = "🗑️ Verlauf gelöscht") }
        }
    }

    fun downloadResult(): Boolean {
        val url = _state.value.resultUrl
        if (url.isBlank()) return false
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = OkHttpClient().newCall(Request.Builder().url(url).build())
                        .execute()
                        .use { it.body?.bytes() ?: error("Leere Datei") }
                    val resolver = getApplication<Application>().contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "veo-product-${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TikTokShop")
                        }
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("Speichern fehlgeschlagen")
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                _state.update { it.copy(toast = "⬇️ Download gestartet") }
            } catch (e: Exception) {
                _state.update { it.copy(toast = "Download fehlgeschlagen: ${e.message}") }
            }
        }
        return true
    }
}
