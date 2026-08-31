package de.spardirekt.tiktokshop.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.spardirekt.tiktokshop.data.image.ImageEncoder
import de.spardirekt.tiktokshop.data.local.SettingsStore
import de.spardirekt.tiktokshop.data.model.GenerateResult
import de.spardirekt.tiktokshop.data.model.ImageKind
import de.spardirekt.tiktokshop.data.model.ImageSlot
import de.spardirekt.tiktokshop.data.model.Tones
import de.spardirekt.tiktokshop.data.model.VideoStyles
import de.spardirekt.tiktokshop.data.model.copyAll
import de.spardirekt.tiktokshop.data.model.masterCopy
import de.spardirekt.tiktokshop.data.model.veoKomplett
import de.spardirekt.tiktokshop.data.remote.ContentGenerator
import de.spardirekt.tiktokshop.data.remote.GenerateException
import de.spardirekt.tiktokshop.data.remote.ProxyContentGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CreatorUiState(
    val apiKey: String = "",
    val showApiKey: Boolean = false,
    val proxyUrl: String = SettingsStore.DEFAULT_PROXY,
    val videoStyle: String = VideoStyles.all.first(),
    val tone: String = Tones.all.first(),
    val images: List<ImageSlot> = defaultSlots(),
    val isGenerating: Boolean = false,
    val error: String? = null,
    val result: GenerateResult? = null,
    val copiedLabel: String? = null,
) {
    val canGenerate: Boolean
        get() = !isGenerating && apiKey.isNotBlank() && images.first().uri != null
}

sealed interface CreatorEvent {
    data class ApiKeyChanged(val value: String) : CreatorEvent
    data object ToggleApiKeyVisibility : CreatorEvent
    data class ProxyUrlChanged(val value: String) : CreatorEvent
    data class VideoStyleChanged(val value: String) : CreatorEvent
    data class ToneChanged(val value: String) : CreatorEvent
    data class ImagePicked(val index: Int, val uri: Uri, val fileName: String?) : CreatorEvent
    data class ImageRemoved(val index: Int) : CreatorEvent
    data object Generate : CreatorEvent
    data object DismissError : CreatorEvent
    data class Copy(val text: String, val label: String) : CreatorEvent
    data object CopyAll : CreatorEvent
    data object CopyMaster : CreatorEvent
    data object CopyVeoKomplett : CreatorEvent
}

class CreatorViewModel(
    private val app: Application,
    private val settings: SettingsStore,
    private val generator: ContentGenerator,
) : ViewModel() {

    private val _state = MutableStateFlow(CreatorUiState())
    val state: StateFlow<CreatorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.proxyUrl.collect { url ->
                _state.update { it.copy(proxyUrl = url) }
            }
        }
    }

    fun onEvent(event: CreatorEvent) {
        when (event) {
            is CreatorEvent.ApiKeyChanged -> _state.update { it.copy(apiKey = event.value, error = null) }
            CreatorEvent.ToggleApiKeyVisibility -> _state.update { it.copy(showApiKey = !it.showApiKey) }
            is CreatorEvent.ProxyUrlChanged -> {
                _state.update { it.copy(proxyUrl = event.value) }
                viewModelScope.launch { settings.setProxyUrl(event.value.trim()) }
            }
            is CreatorEvent.VideoStyleChanged -> _state.update { it.copy(videoStyle = event.value) }
            is CreatorEvent.ToneChanged -> _state.update { it.copy(tone = event.value) }
            is CreatorEvent.ImagePicked -> onImagePicked(event.index, event.uri, event.fileName)
            is CreatorEvent.ImageRemoved -> onImageRemoved(event.index)
            CreatorEvent.Generate -> generate()
            CreatorEvent.DismissError -> _state.update { it.copy(error = null) }
            is CreatorEvent.Copy -> copy(event.text, event.label)
            CreatorEvent.CopyAll -> _state.value.result?.let { copy(it.copyAll(), "Alles kopiert") }
            CreatorEvent.CopyMaster -> _state.value.result?.let { copy(it.masterCopy(), "Alles kopiert") }
            CreatorEvent.CopyVeoKomplett -> _state.value.result?.let { copy(it.veoKomplett(), "Komplett kopiert") }
        }
    }

    private fun onImagePicked(index: Int, uri: Uri, fileName: String?) {
        try {
            app.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Photo picker URIs are readable for the current session without persist.
        }
        _state.update { current ->
            current.copy(
                images = current.images.mapIndexed { i, slot ->
                    if (i == index) slot.copy(uri = uri.toString(), fileName = fileName) else slot
                },
                error = null,
            )
        }
    }

    private fun onImageRemoved(index: Int) {
        _state.update { current ->
            current.copy(
                images = current.images.mapIndexed { i, slot ->
                    if (i == index) slot.copy(uri = null, fileName = null) else slot
                },
            )
        }
    }

    private fun generate() {
        val current = _state.value
        val key = current.apiKey.trim()
        if (key.isEmpty()) {
            _state.update { it.copy(error = "Bitte Anthropic API Key eingeben.") }
            return
        }
        val first = current.images.first()
        if (first.uri == null) {
            _state.update { it.copy(error = "Bitte zuerst ein Produktbild hochladen (Bild 1).") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null, result = null, copiedLabel = null) }
            try {
                val productImages = current.images
                    .filter { it.kind == ImageKind.Product && it.uri != null }
                    .map { slot ->
                        withContext(Dispatchers.IO) {
                            ImageEncoder.encode(app, Uri.parse(slot.uri))
                        }
                    }
                val description = current.images
                    .firstOrNull { it.kind == ImageKind.Description && it.uri != null }
                    ?.let { slot ->
                        withContext(Dispatchers.IO) {
                            ImageEncoder.encode(app, Uri.parse(slot.uri))
                        }
                    }

                val result = withContext(Dispatchers.IO) {
                    generator.generate(
                        apiKey = key,
                        proxyUrl = current.proxyUrl,
                        productImages = productImages,
                        descriptionImage = description,
                        videoStyle = current.videoStyle,
                        tone = current.tone,
                    )
                }
                _state.update { it.copy(isGenerating = false, result = result) }
            } catch (e: GenerateException) {
                _state.update { it.copy(isGenerating = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isGenerating = false, error = e.message ?: "Unbekannter Fehler.") }
            }
        }
    }

    private fun copy(text: String, label: String) {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("TikTok Shop Creator", text))
        _state.update { it.copy(copiedLabel = label) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _state.update { if (it.copiedLabel == label) it.copy(copiedLabel = null) else it }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CreatorViewModel(
                        app = application,
                        settings = SettingsStore(application),
                        generator = ProxyContentGenerator(),
                    ) as T
                }
            }
    }
}

fun defaultSlots(): List<ImageSlot> = listOf(
    ImageSlot(ImageKind.Product),
    ImageSlot(ImageKind.Product),
    ImageSlot(ImageKind.Product),
    ImageSlot(ImageKind.Description),
)

fun validateGenerate(apiKey: String, hasProductImage: Boolean): String? = when {
    apiKey.isBlank() -> "Bitte Anthropic API Key eingeben."
    !hasProductImage -> "Bitte zuerst ein Produktbild hochladen (Bild 1)."
    else -> null
}
