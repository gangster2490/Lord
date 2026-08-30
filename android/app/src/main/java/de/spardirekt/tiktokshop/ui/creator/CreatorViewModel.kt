package de.spardirekt.tiktokshop.ui.creator

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.tiktokshop.TikTokShopApplication
import de.spardirekt.tiktokshop.data.ClaudeApiClient
import de.spardirekt.tiktokshop.data.CreatorOptions
import de.spardirekt.tiktokshop.data.GeneratedContent
import de.spardirekt.tiktokshop.data.ImageEncoder
import de.spardirekt.tiktokshop.data.ImageSlot
import de.spardirekt.tiktokshop.data.ResultFormatter
import de.spardirekt.tiktokshop.data.defaultImageSlots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CreatorUiState(
    val apiKey: String = "",
    val proxyUrl: String = ClaudeApiClient.DEFAULT_PROXY,
    val videoStyle: String = CreatorOptions.videoStyles.first(),
    val tone: String = CreatorOptions.tones.first(),
    val slots: List<ImageSlot> = defaultImageSlots(),
    val loading: Boolean = false,
    val error: String? = null,
    val result: GeneratedContent? = null,
    val copiedKey: String? = null,
)

class CreatorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TikTokShopApplication
    private val _state = MutableStateFlow(CreatorUiState())
    val state: StateFlow<CreatorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            app.preferences.snapshot.collect { prefs ->
                _state.update {
                    it.copy(
                        apiKey = if (it.apiKey.isBlank()) prefs.anthropicKey else it.apiKey,
                        proxyUrl = prefs.proxyUrl,
                        videoStyle = prefs.videoStyle,
                        tone = prefs.tone,
                    )
                }
            }
        }
    }

    fun onApiKeyChange(value: String) {
        _state.update { it.copy(apiKey = value, error = null) }
        viewModelScope.launch { app.preferences.setAnthropicKey(value) }
    }

    fun onProxyChange(value: String) {
        _state.update { it.copy(proxyUrl = value) }
        viewModelScope.launch { app.preferences.setProxyUrl(value) }
    }

    fun onStyleChange(value: String) {
        _state.update { it.copy(videoStyle = value) }
        viewModelScope.launch { app.preferences.setVideoStyle(value) }
    }

    fun onToneChange(value: String) {
        _state.update { it.copy(tone = value) }
        viewModelScope.launch { app.preferences.setTone(value) }
    }

    fun onImagePicked(index: Int, uri: Uri) {
        viewModelScope.launch {
            try {
                val encoded = withContext(Dispatchers.IO) {
                    ImageEncoder.encode(getApplication<Application>().contentResolver, uri)
                }
                _state.update { current ->
                    current.copy(
                        error = null,
                        slots = current.slots.map { slot ->
                            if (slot.index == index) slot.copy(image = encoded) else slot
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Bild konnte nicht geladen werden.") }
            }
        }
    }

    fun onImageRemoved(index: Int) {
        _state.update { current ->
            current.copy(
                slots = current.slots.map { slot ->
                    if (slot.index == index) slot.copy(image = null) else slot
                },
            )
        }
    }

    fun generate() {
        val snapshot = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, result = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    app.claudeApi.generate(
                        proxyUrl = snapshot.proxyUrl,
                        apiKey = snapshot.apiKey,
                        slots = snapshot.slots,
                        style = snapshot.videoStyle,
                        tone = snapshot.tone,
                    )
                }
                _state.update { it.copy(loading = false, result = result) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Unbekannter Fehler.") }
            }
        }
    }

    fun copyText(key: String, text: String): String {
        _state.update { it.copy(copiedKey = key) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _state.update { if (it.copiedKey == key) it.copy(copiedKey = null) else it }
        }
        return text
    }

    fun textFor(key: String, result: GeneratedContent): String = when (key) {
        "facts" -> ResultFormatter.formatFactsPlain(result.productFacts)
        "title" -> result.title
        "hooks" -> result.hooks.mapIndexed { i, h -> "${i + 1}. $h" }.joinToString("\n")
        "hashtags" -> result.hashtags.joinToString(" ")
        "banner" -> result.bannerText.joinToString("\n")
        "bannerPrompt" -> result.bannerPrompt
        "voice" -> result.voiceoverText
        "music" -> result.musicSuggestion
        "sfx" -> result.soundEffects
        "veo" -> result.veoPrompt
        "live" -> result.liveScript
        "master" -> ResultFormatter.buildMasterText(result)
        "veoKomplett" -> ResultFormatter.buildVeoKomplett(result)
        "all" -> ResultFormatter.buildCopyAll(result)
        else -> ""
    }
}
