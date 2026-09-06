package de.spardirekt.clipforge.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.spardirekt.clipforge.data.image.ImageEncoder
import de.spardirekt.clipforge.data.local.HistoryStore
import de.spardirekt.clipforge.data.local.PhotoStore
import de.spardirekt.clipforge.data.local.SecureApiKeyStore
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Tab { STUDIO, ARCHIVE, SETTINGS }

sealed interface PendingConfirm {
    data object NewProject : PendingConfirm
    data object ClearKey : PendingConfirm
    data object ClearArchive : PendingConfirm
    data class DeleteHistory(val id: String, val name: String) : PendingConfirm
}

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
    val generateStage: String? = null,
    val isTestingKey: Boolean = false,
    val error: String? = null,
    val settingsMessage: String? = null,
    val result: AdPackage? = null,
    val resultId: String? = null,
    val history: List<HistoryEntry> = emptyList(),
    val copiedLabel: String? = null,
    val pendingConfirm: PendingConfirm? = null,
    val savedKeyMasked: String = "",
) {
    val canGenerate: Boolean
        get() = !isGenerating && apiKey.isNotBlank() && photos.isNotEmpty()

    val generateBlockedReason: String?
        get() = when {
            isGenerating -> null
            apiKey.isBlank() -> "Нужен ключ в Настройках или sk-demo"
            photos.isEmpty() -> "Добавьте хотя бы одно фото товара"
            else -> null
        }

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
    data object RequestNewProject : StudioEvent
    data class ApiKeyChanged(val value: String) : StudioEvent
    data object ToggleApiKeyVisibility : StudioEvent
    data object SaveApiKey : StudioEvent
    data object RequestClearApiKey : StudioEvent
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
    data object CancelGenerate : StudioEvent
    data object DismissError : StudioEvent
    data class Copy(val text: String, val label: String) : StudioEvent
    data object CopyCaption : StudioEvent
    data object CopyVeo : StudioEvent
    data object CopyAll : StudioEvent
    data object ShareVeo : StudioEvent
    data object ShareAll : StudioEvent
    data object Regenerate : StudioEvent
    data class OpenHistory(val id: String) : StudioEvent
    data class RequestDeleteHistory(val id: String) : StudioEvent
    data object RequestClearArchive : StudioEvent
    data object ConfirmPending : StudioEvent
    data object DismissConfirm : StudioEvent
}

class StudioViewModel(
    private val app: Application,
    private val settings: SettingsStore,
    private val keys: SecureApiKeyStore,
    private val history: HistoryStore,
    private val liveGenerator: AdGenerator = OpenAiAdGenerator(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        StudioUiState(apiKey = keys.getKey(), savedKeyMasked = keys.maskedPreview()),
    )
    val state: StateFlow<StudioUiState> = _state.asStateFlow()
    private var generateJob: Job? = null

    init {
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
            settings.wish.collect { text -> _state.update { it.copy(wish = text) } }
        }
        viewModelScope.launch {
            history.entries.collect { list -> _state.update { it.copy(history = list) } }
        }
    }

    fun onEvent(event: StudioEvent) {
        when (event) {
            is StudioEvent.OpenTab -> _state.update { it.copy(tab = event.tab, showResult = false) }
            StudioEvent.CloseResult -> _state.update { it.copy(showResult = false) }
            StudioEvent.RequestNewProject -> _state.update { it.copy(pendingConfirm = PendingConfirm.NewProject) }
            is StudioEvent.ApiKeyChanged -> _state.update {
                it.copy(apiKey = event.value, error = null, settingsMessage = null)
            }
            StudioEvent.ToggleApiKeyVisibility -> _state.update { it.copy(showApiKey = !it.showApiKey) }
            StudioEvent.SaveApiKey -> {
                keys.saveKey(_state.value.apiKey)
                _state.update { it.copy(savedKeyMasked = keys.maskedPreview()) }
                flashSettings("Ключ сохранён в защищённом хранилище.")
            }
            StudioEvent.RequestClearApiKey -> _state.update { it.copy(pendingConfirm = PendingConfirm.ClearKey) }
            StudioEvent.TestApiKey -> testKey()
            is StudioEvent.PhotosPicked -> onPhotosPicked(event.uris, event.names)
            is StudioEvent.PhotoRemoved -> removePhoto(event.uri)
            StudioEvent.ClearPhotos -> clearStudioPhotos()
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
            is StudioEvent.WishChanged -> {
                val text = event.value.take(280)
                _state.update { it.copy(wish = text) }
                viewModelScope.launch { settings.setWish(text) }
            }
            StudioEvent.Generate, StudioEvent.Regenerate -> generate()
            StudioEvent.CancelGenerate -> cancelGenerate()
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
            StudioEvent.ShareVeo -> _state.value.result?.let {
                share(it.copyVeoPack(_state.value.length), "ClipForge Veo")
            }
            StudioEvent.ShareAll -> _state.value.result?.let { ad ->
                val s = _state.value
                share(ad.copyAll(s.platform, s.length, s.formula, s.language), "ClipForge пакет")
            }
            is StudioEvent.OpenHistory -> openHistory(event.id)
            is StudioEvent.RequestDeleteHistory -> {
                val entry = _state.value.history.firstOrNull { it.id == event.id } ?: return
                _state.update {
                    it.copy(pendingConfirm = PendingConfirm.DeleteHistory(entry.id, entry.productName))
                }
            }
            StudioEvent.RequestClearArchive -> _state.update {
                it.copy(pendingConfirm = PendingConfirm.ClearArchive)
            }
            StudioEvent.ConfirmPending -> confirmPending()
            StudioEvent.DismissConfirm -> _state.update { it.copy(pendingConfirm = null) }
        }
    }

    private fun confirmPending() {
        val pending = _state.value.pendingConfirm ?: return
        _state.update { it.copy(pendingConfirm = null) }
        when (pending) {
            PendingConfirm.NewProject -> resetProject()
            PendingConfirm.ClearKey -> {
                keys.removeKey()
                _state.update { it.copy(apiKey = "", savedKeyMasked = "", settingsMessage = "Ключ удалён.") }
            }
            PendingConfirm.ClearArchive -> viewModelScope.launch {
                history.clear()
                PhotoStore.clearThumbs(app)
                PhotoStore.clearPhotos(app)
            }
            is PendingConfirm.DeleteHistory -> viewModelScope.launch {
                history.remove(pending.id)
                PhotoStore.deleteThumb(app, pending.id)
            }
        }
    }

    private fun resetProject() {
        clearStudioPhotos()
        _state.update {
            it.copy(
                showResult = false,
                tab = Tab.STUDIO,
                result = null,
                resultId = null,
                error = null,
                wish = "",
                generateStage = null,
            )
        }
        viewModelScope.launch { settings.setWish("") }
    }

    private fun onPhotosPicked(uris: List<Uri>, names: List<String?>) {
        val existing = _state.value.photos.map { it.uri }.toSet()
        val added = uris.mapIndexedNotNull { index, uri ->
            val persisted = PhotoStore.persistPicked(app, uri)
            if (persisted in existing) {
                null
            } else {
                ProductPhoto(uri = persisted, fileName = names.getOrNull(index))
            }
        }
        _state.update { current ->
            current.copy(
                photos = (current.photos + added).take(MAX_PHOTOS),
                error = null,
            )
        }
    }

    private fun removePhoto(uri: String) {
        _state.update { current ->
            current.copy(photos = current.photos.filterNot { it.uri == uri })
        }
    }

    private fun clearStudioPhotos() {
        _state.update { it.copy(photos = emptyList()) }
    }

    private fun generate() {
        val current = _state.value
        val problem = validateGenerate(current.apiKey, current.photos.size)
        if (problem != null) {
            _state.update { it.copy(error = problem, tab = Tab.STUDIO, showResult = false) }
            return
        }
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isGenerating = true,
                    generateStage = "Читаю фото…",
                    error = null,
                    copiedLabel = null,
                )
            }
            try {
                val images = current.photos.map { photo ->
                    withContext(Dispatchers.IO) {
                        ImageEncoder.encode(app, Uri.parse(photo.uri))
                    }
                }
                _state.update { it.copy(generateStage = "Собираю ролик…") }
                val generator = adGeneratorFor(current.apiKey, liveGenerator)
                val result = withContext(Dispatchers.IO) {
                    generator.generate(current.apiKey, images, current.brief)
                }
                _state.update { it.copy(generateStage = "Сохраняю пакет…") }
                val id = UUID.randomUUID().toString()
                val thumb = current.photos.firstOrNull()?.let { photo ->
                    PhotoStore.persistThumb(app, id, Uri.parse(photo.uri))
                }
                val entry = HistoryEntry(
                    id = id,
                    createdAt = System.currentTimeMillis(),
                    platformId = current.platform.id,
                    lengthSeconds = current.length.seconds,
                    formulaId = current.formula.id,
                    languageId = current.language.id,
                    styleId = current.style.id,
                    productName = result.product.name,
                    thumbnailUri = thumb,
                    wish = current.wish,
                    photoUris = current.photos.map { it.uri },
                    ad = result,
                )
                history.upsert(entry)
                _state.update {
                    it.copy(
                        isGenerating = false,
                        generateStage = null,
                        result = result,
                        resultId = entry.id,
                        showResult = true,
                    )
                }
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(isGenerating = false, generateStage = null)
                }
                throw e
            } catch (e: GenerateException) {
                _state.update {
                    it.copy(isGenerating = false, generateStage = null, error = e.message)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isGenerating = false,
                        generateStage = null,
                        error = e.message ?: "Неизвестная ошибка.",
                    )
                }
            }
        }
    }

    private fun cancelGenerate() {
        generateJob?.cancel()
        generateJob = null
        _state.update { it.copy(isGenerating = false, generateStage = null) }
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
                keys.saveKey(key)
                _state.update {
                    it.copy(apiKey = key, savedKeyMasked = keys.maskedPreview(), isTestingKey = false, settingsMessage = message)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isTestingKey = false, error = e.message ?: "Ключ не принят.")
                }
            }
        }
    }

    private fun openHistory(id: String) {
        val entry = _state.value.history.firstOrNull { it.id == id } ?: return
        val platform = Platform.fromId(entry.platformId)
        val length = AdLength.fromSeconds(entry.lengthSeconds)
        val formula = AdFormula.fromId(entry.formulaId)
        val language = AdLanguage.fromId(entry.languageId)
        val style = VisualStyle.fromId(entry.styleId)
        _state.update {
            it.copy(
                result = entry.ad,
                resultId = entry.id,
                platform = platform,
                length = length,
                formula = formula,
                language = language,
                style = style,
                wish = entry.wish,
                photos = entry.photoUris.map { uri -> ProductPhoto(uri) },
                showResult = true,
                error = null,
            )
        }
        viewModelScope.launch {
            settings.setPlatform(platform)
            settings.setLength(length)
            settings.setFormula(formula)
            settings.setLanguage(language)
            settings.setStyle(style)
            settings.setWish(entry.wish)
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

    private fun share(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
                        keys = SecureApiKeyStore(application),
                        history = HistoryStore(application),
                    ) as T
                }
            }
    }
}
