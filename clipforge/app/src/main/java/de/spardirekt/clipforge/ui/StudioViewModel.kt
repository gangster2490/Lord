package de.spardirekt.clipforge.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.spardirekt.clipforge.data.image.ImageEncoder
import de.spardirekt.clipforge.data.local.HistoryStore
import de.spardirekt.clipforge.data.local.SettingsStore
import de.spardirekt.clipforge.data.model.AdFormula
import de.spardirekt.clipforge.data.model.AdLanguage
import de.spardirekt.clipforge.data.model.AdLength
import de.spardirekt.clipforge.data.model.AdPackage
import de.spardirekt.clipforge.data.model.GenerateBrief
import de.spardirekt.clipforge.data.model.HistoryEntry
import de.spardirekt.clipforge.data.model.Platform
import de.spardirekt.clipforge.data.model.ProductPhoto
import de.spardirekt.clipforge.data.model.VisualStyle
import de.spardirekt.clipforge.data.model.copyAll
import de.spardirekt.clipforge.data.model.copyCaptionPack
import de.spardirekt.clipforge.data.model.copyVeoPack
import de.spardirekt.clipforge.data.model.validateGenerate
import de.spardirekt.clipforge.data.remote.AdGenerator
import de.spardirekt.clipforge.data.remote.GenerateException
import de.spardirekt.clipforge.data.remote.OpenAiAdGenerator
import de.spardirekt.clipforge.data.remote.adGeneratorFor
import de.spardirekt.clipforge.data.remote.isDemoKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Tab { STUDIO, ARCHIVE, SETTINGS }

data class StudioUiState(
    val tab: Tab = Tab.STUDIO,
    val showResult: Boolean = false,
    val apiKey: String = "",
    val showApiKey: Boolean = false,
    val photos: List<ProductPhoto> = emptyList(),
    val platform: Platform = Platform.TIKTOK_SHOP,
    val length: AdLength = AdLength.EIGHT,
    val formula: AdFormula = AdFormula.HOOK_DEMO_CTA,
    val style: VisualStyle = VisualStyle.CINEMATIC,
    val language: AdLanguage = AdLanguage.RU,
    val wish: String = "",
    val isGenerating: Boolean = false,
    val isTestingKey: Boolean = false,
    val error: String? = null,
    val settingsMessage: String? = null,
    val result: AdPackage? = null,
    val resultId: String? = null,
    val history: List<HistoryEntry> = emptyList(),
    val copiedLabel: String? = null,
) {
    val canGenerate: Boolean
        get() = !isGenerating && apiKey.isNotBlank() && photos.isNotEmpty()

    val isDemo: Boolean
        get() = isDemoKey(apiKey)

    val brief: GenerateBrief
        get() = GenerateBrief(
            platform = platform,
            length = length,
            formula = formula,
            style = style,
            language = language,
            wish = wish,
            photoCount = photos.size,
        )
}

sealed interface StudioEvent {
    data class OpenTab(val tab: Tab) : StudioEvent
    data object CloseResult : StudioEvent
    data object NewProject : StudioEvent
    data class ApiKeyChanged(val value: String) : StudioEvent
    data object ToggleApiKeyVisibility : StudioEvent
    data object SaveApiKey : StudioEvent
    data object ClearApiKey : StudioEvent
    data object TestApiKey : StudioEvent
    data class PhotosPicked(val uris: List<Uri>, val names: List<String?>) : StudioEvent
    data class PhotoRemoved(val uri: String) : StudioEvent
    data object ClearPhotos : StudioEvent
    data class PlatformChanged(val value: Platform) : StudioEvent
    data class LengthChanged(val value: AdLength) : StudioEvent
    data class FormulaChanged(val value: AdFormula) : StudioEvent
    data class StyleChanged(val value: VisualStyle) : StudioEvent
    data class LanguageChanged(val value: AdLanguage) : StudioEvent
    data class WishChanged(val value: String) : StudioEvent
    data object Generate : StudioEvent
    data object DismissError : StudioEvent
    data class Copy(val text: String, val label: String) : StudioEvent
    data object CopyCaption : StudioEvent
    data object CopyVeo : StudioEvent
    data object CopyAll : StudioEvent
    data class OpenHistory(val id: String) : StudioEvent
    data class DeleteHistory(val id: String) : StudioEvent
}

class StudioViewModel(
    private val app: Application,
    private val settings: SettingsStore,
    private val history: HistoryStore,
    private val liveGenerator: AdGenerator = OpenAiAdGenerator(),
) : ViewModel() {

    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.apiKey.collect { key -> _state.update { it.copy(apiKey = key) } }
        }
        viewModelScope.launch {
            settings.platformId.collect { id ->
                _state.update { it.copy(platform = Platform.fromId(id)) }
            }
        }
        viewModelScope.launch {
            settings.lengthSeconds.collect { sec ->
                _state.update { it.copy(length = AdLength.fromSeconds(sec)) }
            }
        }
        viewModelScope.launch {
            settings.formulaId.collect { id ->
                _state.update { it.copy(formula = AdFormula.fromId(id)) }
            }
        }
        viewModelScope.launch {
            settings.styleId.collect { id ->
                _state.update { it.copy(style = VisualStyle.fromId(id)) }
            }
        }
        viewModelScope.launch {
            settings.languageId.collect { id ->
                _state.update { it.copy(language = AdLanguage.fromId(id)) }
            }
        }
        viewModelScope.launch {
            history.entries.collect { list -> _state.update { it.copy(history = list) } }
        }
    }

    fun onEvent(event: StudioEvent) {
        when (event) {
            is StudioEvent.OpenTab -> _state.update { it.copy(tab = event.tab, showResult = false) }
            StudioEvent.CloseResult -> _state.update { it.copy(showResult = false) }
            StudioEvent.NewProject -> _state.update {
                it.copy(
                    showResult = false,
                    tab = Tab.STUDIO,
                    result = null,
                    resultId = null,
                    error = null,
                    photos = emptyList(),
                    wish = "",
                )
            }
            is StudioEvent.ApiKeyChanged -> _state.update {
                it.copy(apiKey = event.value, error = null, settingsMessage = null)
            }
            StudioEvent.ToggleApiKeyVisibility -> _state.update { it.copy(showApiKey = !it.showApiKey) }
            StudioEvent.SaveApiKey -> viewModelScope.launch {
                settings.setApiKey(_state.value.apiKey)
                flashSettings("Ключ сохранён на этом устройстве.")
            }
            StudioEvent.ClearApiKey -> viewModelScope.launch {
                settings.setApiKey("")
                _state.update { it.copy(apiKey = "", settingsMessage = "Ключ удалён.") }
            }
            StudioEvent.TestApiKey -> testKey()
            is StudioEvent.PhotosPicked -> onPhotosPicked(event.uris, event.names)
            is StudioEvent.PhotoRemoved -> _state.update { current ->
                current.copy(photos = current.photos.filterNot { it.uri == event.uri })
            }
            StudioEvent.ClearPhotos -> _state.update { it.copy(photos = emptyList()) }
            is StudioEvent.PlatformChanged -> {
                _state.update { it.copy(platform = event.value) }
                viewModelScope.launch { settings.setPlatform(event.value) }
            }
            is StudioEvent.LengthChanged -> {
                _state.update { it.copy(length = event.value) }
                viewModelScope.launch { settings.setLength(event.value) }
            }
            is StudioEvent.FormulaChanged -> {
                _state.update { it.copy(formula = event.value) }
                viewModelScope.launch { settings.setFormula(event.value) }
            }
            is StudioEvent.StyleChanged -> {
                _state.update { it.copy(style = event.value) }
                viewModelScope.launch { settings.setStyle(event.value) }
            }
            is StudioEvent.LanguageChanged -> {
                _state.update { it.copy(language = event.value) }
                viewModelScope.launch { settings.setLanguage(event.value) }
            }
            is StudioEvent.WishChanged -> _state.update { it.copy(wish = event.value.take(280)) }
            StudioEvent.Generate -> generate()
            StudioEvent.DismissError -> _state.update { it.copy(error = null) }
            is StudioEvent.Copy -> copy(event.text, event.label)
            StudioEvent.CopyCaption -> _state.value.result?.let {
                copy(it.copyCaptionPack(_state.value.platform), "Подпись скопирована")
            }
            StudioEvent.CopyVeo -> _state.value.result?.let {
                copy(it.copyVeoPack(_state.value.length), "Пакет Veo скопирован")
            }
            StudioEvent.CopyAll -> _state.value.result?.let { ad ->
                val s = _state.value
                copy(ad.copyAll(s.platform, s.length, s.formula, s.language), "Весь пакет скопирован")
            }
            is StudioEvent.OpenHistory -> openHistory(event.id)
            is StudioEvent.DeleteHistory -> viewModelScope.launch { history.remove(event.id) }
        }
    }

    private fun onPhotosPicked(uris: List<Uri>, names: List<String?>) {
        val existing = _state.value.photos.map { it.uri }.toSet()
        val added = uris.mapIndexedNotNull { index, uri ->
            val value = uri.toString()
            if (value in existing) {
                null
            } else {
                try {
                    app.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                    // Photo picker URIs are readable for the current session.
                }
                ProductPhoto(uri = value, fileName = names.getOrNull(index))
            }
        }
        _state.update { current ->
            current.copy(
                photos = (current.photos + added).take(MAX_PHOTOS),
                error = null,
            )
        }
    }

    private fun generate() {
        val current = _state.value
        val problem = validateGenerate(current.apiKey, current.photos.size)
        if (problem != null) {
            _state.update { it.copy(error = problem, tab = Tab.STUDIO) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null, copiedLabel = null) }
            try {
                val images = current.photos.map { photo ->
                    withContext(Dispatchers.IO) {
                        ImageEncoder.encode(app, Uri.parse(photo.uri))
                    }
                }
                val generator = adGeneratorFor(current.apiKey, liveGenerator)
                val result = withContext(Dispatchers.IO) {
                    generator.generate(current.apiKey, images, current.brief)
                }
                val entry = HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                    platformId = current.platform.id,
                    lengthSeconds = current.length.seconds,
                    formulaId = current.formula.id,
                    languageId = current.language.id,
                    productName = result.product.name,
                    thumbnailUri = current.photos.firstOrNull()?.uri,
                    ad = result,
                )
                history.upsert(entry)
                _state.update {
                    it.copy(
                        isGenerating = false,
                        result = result,
                        resultId = entry.id,
                        showResult = true,
                        tab = Tab.STUDIO,
                    )
                }
            } catch (e: GenerateException) {
                _state.update { it.copy(isGenerating = false, error = e.message, showResult = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isGenerating = false, error = e.message ?: "Неизвестная ошибка.", showResult = false)
                }
            }
        }
    }

    private fun testKey() {
        val key = _state.value.apiKey
        viewModelScope.launch {
            _state.update { it.copy(isTestingKey = true, settingsMessage = null, error = null) }
            try {
                val message = withContext(Dispatchers.IO) {
                    if (isDemoKey(key)) {
                        "Демо-режим готов — генерация идёт локально."
                    } else {
                        OpenAiAdGenerator().testConnection(key)
                    }
                }
                settings.setApiKey(key)
                _state.update { it.copy(isTestingKey = false, settingsMessage = message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isTestingKey = false, error = e.message ?: "Ключ не принят.")
                }
            }
        }
    }

    private fun openHistory(id: String) {
        val entry = _state.value.history.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                result = entry.ad,
                resultId = entry.id,
                platform = Platform.fromId(entry.platformId),
                length = AdLength.fromSeconds(entry.lengthSeconds),
                formula = AdFormula.fromId(entry.formulaId),
                language = AdLanguage.fromId(entry.languageId),
                showResult = true,
                tab = Tab.STUDIO,
                error = null,
            )
        }
    }

    private fun copy(text: String, label: String) {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ClipForge", text))
        _state.update { it.copy(copiedLabel = label) }
        viewModelScope.launch {
            delay(2000)
            _state.update { if (it.copiedLabel == label) it.copy(copiedLabel = null) else it }
        }
    }

    private fun flashSettings(message: String) {
        _state.update { it.copy(settingsMessage = message) }
        viewModelScope.launch {
            delay(2200)
            _state.update { if (it.settingsMessage == message) it.copy(settingsMessage = null) else it }
        }
    }

    companion object {
        const val MAX_PHOTOS = 8

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StudioViewModel(
                        app = application,
                        settings = SettingsStore(application),
                        history = HistoryStore(application),
                    ) as T
                }
            }
    }
}
