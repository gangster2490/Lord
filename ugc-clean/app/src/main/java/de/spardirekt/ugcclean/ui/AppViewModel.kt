package de.spardirekt.ugcclean.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.ugcclean.R
import de.spardirekt.ugcclean.UgcCleanApp
import de.spardirekt.ugcclean.gen.StartGate
import de.spardirekt.ugcclean.model.ProjectRecord
import de.spardirekt.ugcclean.model.ProjectStatus
import de.spardirekt.ugcclean.model.SpeechLanguage
import de.spardirekt.ugcclean.net.AiProviderId
import de.spardirekt.ugcclean.net.ProviderClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { CREATE, HISTORY, SETTINGS }

data class ProviderKeyUi(
    val draft: String = "",
    val masked: Boolean = true,
    val saved: Boolean = false,
)

data class UiState(
    val tab: Tab = Tab.CREATE,
    val photos: List<String> = emptyList(),
    val language: SpeechLanguage = SpeechLanguage.DE,
    val hasKey: Boolean = false,
    val toast: String? = null,
    val history: List<ProjectRecord> = emptyList(),
    val active: ProjectRecord? = null,
    val opened: ProjectRecord? = null,
    val showResult: Boolean = false,
    val provider: AiProviderId = AiProviderId.OPENAI,
    val openai: ProviderKeyUi = ProviderKeyUi(),
    val gemini: ProviderKeyUi = ProviderKeyUi(),
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as UgcCleanApp
    private val _state = MutableStateFlow(loadUi())
    val state: StateFlow<UiState> = _state
    private val clients = ProviderClients()

    init {
        refreshHistory()
        viewModelScope.launch {
            app.session.active.collect { active ->
                _state.update { ui ->
                    val openResult = active?.status == ProjectStatus.READY && ui.active?.id == active.id
                    ui.copy(
                        active = active,
                        opened = if (openResult) active else ui.opened,
                        showResult = ui.showResult || openResult,
                        history = app.projects.list(),
                    )
                }
            }
        }
    }

    fun selectTab(tab: Tab) = _state.update { it.copy(tab = tab, showResult = false) }

    fun addPhotos(uris: List<String>) {
        _state.update { ui ->
            val merged = (ui.photos + uris).distinct().take(StartGate.MAX_PHOTOS)
            ui.copy(photos = merged)
        }
    }

    fun removePhoto(uri: String) = _state.update { it.copy(photos = it.photos.filterNot { p -> p == uri }) }

    fun clearPhotos() = _state.update { it.copy(photos = emptyList()) }

    fun setLanguage(language: SpeechLanguage) = _state.update { it.copy(language = language) }

    fun start() {
        val ui = _state.value
        val err = app.session.start(ui.photos, ui.language)
        if (err != null) {
            _state.update { it.copy(toast = err) }
        }
    }

    fun retry() {
        val id = _state.value.active?.id ?: return
        val err = app.session.retry(id)
        if (err != null) _state.update { it.copy(toast = err) }
    }

    fun openProject(id: String) {
        val record = app.projects.get(id) ?: return
        _state.update {
            it.copy(
                opened = record,
                showResult = record.status == ProjectStatus.READY,
                tab = Tab.CREATE,
                photos = record.photoUris,
                language = record.language,
            )
        }
    }

    fun newProject() {
        _state.update {
            it.copy(photos = emptyList(), opened = null, showResult = false, tab = Tab.CREATE)
        }
    }

    fun closeResult() = _state.update { it.copy(showResult = false) }

    fun showToast(message: String) = _state.update { it.copy(toast = message) }

    fun markSaved() {
        val record = _state.value.opened ?: return
        val saved = record.copy(updatedAt = System.currentTimeMillis())
        app.projects.save(saved)
        _state.update {
            it.copy(
                opened = saved,
                toast = getApplication<Application>().getString(R.string.project_saved),
                history = app.projects.list(),
            )
        }
    }

    fun deleteProject(id: String) {
        app.projects.delete(id)
        refreshHistory()
        _state.update { ui ->
            if (ui.opened?.id == id) ui.copy(opened = null, showResult = false) else ui
        }
    }

    fun setProvider(id: AiProviderId) {
        app.settings.setProvider(id)
        _state.update { it.copy(provider = id, hasKey = app.settings.hasKey(id)) }
    }

    fun setKeyDraft(id: AiProviderId, value: String) {
        _state.update { ui -> ui.copyProvider(id) { it.copy(draft = value, masked = false) } }
    }

    fun toggleKeyMask(id: AiProviderId) {
        _state.update { ui -> ui.copyProvider(id) { it.copy(masked = !it.masked) } }
    }

    fun saveKey(id: AiProviderId) {
        val draft = _state.value.keyUi(id).draft.trim()
        if (draft.isBlank() || draft.startsWith("••")) {
            _state.update { it.copy(toast = "Bitte einen Key einfügen.") }
            return
        }
        app.settings.saveKey(draft, id)
        _state.update { ui ->
            ui.copyProvider(id) { ProviderKeyUi(draft = "••••••••", masked = true, saved = true) }
                .copy(hasKey = app.settings.hasKey(), toast = "Key gespeichert")
        }
    }

    fun removeKey(id: AiProviderId) {
        app.settings.clearKey(id)
        _state.update { ui ->
            ui.copyProvider(id) { ProviderKeyUi() }
                .copy(hasKey = app.settings.hasKey(), toast = "Key gelöscht")
        }
    }

    fun testKey(id: AiProviderId) {
        viewModelScope.launch {
            val stored = app.settings.apiKey(id)
            val draft = _state.value.keyUi(id).draft.trim()
            val key = when {
                stored.isNotBlank() -> stored
                draft.isNotBlank() && !draft.startsWith("••") -> draft
                else -> ""
            }
            if (key.isBlank()) {
                _state.update { it.copy(toast = "Bitte zuerst einen API-Key speichern.") }
                return@launch
            }
            val msg = withContext(Dispatchers.IO) {
                runCatching { clients.test(id, key) }.getOrElse { it.message ?: "Test fehlgeschlagen" }
            }
            _state.update { it.copy(toast = msg, hasKey = app.settings.hasKey()) }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    fun canStart(): Boolean {
        val ui = _state.value
        return StartGate.canStart(ui.photos.size, ui.hasKey || app.settings.hasKey(), app.session.isRunning())
    }

    private fun refreshHistory() {
        _state.update { loadUi().copy(tab = it.tab, photos = it.photos, language = it.language, showResult = it.showResult, opened = it.opened, active = it.active, toast = it.toast) }
    }

    private fun loadUi(): UiState {
        val provider = app.settings.provider()
        return UiState(
            hasKey = app.settings.hasKey(),
            history = app.projects.list(),
            provider = provider,
            openai = storedKeyUi(AiProviderId.OPENAI),
            gemini = storedKeyUi(AiProviderId.GEMINI),
        )
    }

    private fun storedKeyUi(id: AiProviderId): ProviderKeyUi {
        val saved = app.settings.hasKey(id)
        return ProviderKeyUi(draft = if (saved) "••••••••" else "", masked = true, saved = saved)
    }
}

private fun UiState.keyUi(id: AiProviderId): ProviderKeyUi =
    if (id == AiProviderId.OPENAI) openai else gemini

private fun UiState.copyProvider(id: AiProviderId, transform: (ProviderKeyUi) -> ProviderKeyUi): UiState =
    if (id == AiProviderId.OPENAI) copy(openai = transform(openai)) else copy(gemini = transform(gemini))
