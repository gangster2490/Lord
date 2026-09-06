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
import de.spardirekt.ugcclean.net.Keys
import de.spardirekt.ugcclean.net.OpenAiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { CREATE, HISTORY, SETTINGS }

data class UiState(
    val tab: Tab = Tab.CREATE,
    val photos: List<String> = emptyList(),
    val language: SpeechLanguage = SpeechLanguage.DE,
    val hasKey: Boolean = false,
    val keyDraft: String = "",
    val keyMasked: Boolean = true,
    val toast: String? = null,
    val history: List<ProjectRecord> = emptyList(),
    val active: ProjectRecord? = null,
    val opened: ProjectRecord? = null,
    val showResult: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as UgcCleanApp
    private val _state = MutableStateFlow(
        UiState(hasKey = app.settings.hasKey(), keyDraft = if (app.settings.hasKey()) "••••••••" else ""),
    )
    val state: StateFlow<UiState> = _state
    private val openAi = OpenAiClient()

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

    fun setKeyDraft(value: String) = _state.update { it.copy(keyDraft = value, keyMasked = false) }

    fun toggleKeyMask() = _state.update { it.copy(keyMasked = !it.keyMasked) }

    fun saveKey() {
        val value = _state.value.keyDraft.trim()
        if (value.isBlank() || value.startsWith("••")) {
            _state.update { it.copy(toast = "Bitte einen Key einfügen.") }
            return
        }
        app.settings.saveKey(value)
        _state.update { it.copy(hasKey = true, keyDraft = "••••••••", keyMasked = true, toast = "Key gespeichert") }
    }

    fun removeKey() {
        app.settings.clearKey()
        _state.update { it.copy(hasKey = false, keyDraft = "", toast = "Key gelöscht") }
    }

    fun testKey() {
        viewModelScope.launch {
            val stored = app.settings.apiKey()
            val draft = _state.value.keyDraft.trim()
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
                runCatching { openAi.testConnection(key) }.getOrElse { it.message ?: "Test fehlgeschlagen" }
            }
            _state.update { it.copy(toast = msg, hasKey = app.settings.hasKey() || Keys.isDemo(key)) }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    fun canStart(): Boolean {
        val ui = _state.value
        return StartGate.canStart(ui.photos.size, ui.hasKey || app.settings.hasKey(), app.session.isRunning())
    }

    private fun refreshHistory() {
        _state.update { it.copy(history = app.projects.list(), hasKey = app.settings.hasKey()) }
    }
}
