package de.spardirekt.recipeveo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.spardirekt.recipeveo.domain.CreativeMode
import de.spardirekt.recipeveo.domain.Project
import de.spardirekt.recipeveo.domain.SeedProjects
import de.spardirekt.recipeveo.domain.StudioRules
import de.spardirekt.recipeveo.domain.StudioState
import de.spardirekt.recipeveo.domain.StudioStore
import de.spardirekt.recipeveo.domain.ThemeMode
import de.spardirekt.recipeveo.domain.VoiceLang
import de.spardirekt.recipeveo.domain.ensureDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StudioViewModel(
    private val store: StudioStore,
    private val photos: PhotoStore,
) : ViewModel() {
    val ready: StateFlow<Boolean> = store.ready
    val state: StateFlow<StudioState> = store.state
    private val _openResultId = MutableStateFlow<String?>(null)
    val openResultId: StateFlow<String?> = _openResultId

    init {
        viewModelScope.launch {
            store.hydrate()
            store.update { it.ensureDraft(store.clock) }
        }
    }

    fun consumeResultNav() {
        _openResultId.value = null
    }

    fun newProject() = update { it.ensureDraft(store.clock) }

    fun open(id: String) = update { it.open(id) }

    fun addPhotos(uris: List<String>) = update { state ->
        val project = state.active() ?: return@update state
        val existing = project.photos.map { it.uri }.toSet()
        val incoming = uris.filterNot { it in existing }
        if (incoming.isEmpty()) return@update state
        val added = photos.persist(project.id, incoming)
        val nextPhotos = (project.photos + added).take(StudioRules.MAX_PHOTOS)
        state.upsert(project.copy(photos = nextPhotos, updatedAt = store.clock.nowMillis()))
    }

    fun addDemoPhoto() = addPhotos(listOf(SeedProjects.DEMO_PHOTO))

    fun removePhoto(id: String) = update { state ->
        val project = state.active() ?: return@update state
        photos.deletePhoto(project.id, id)
        state.upsert(project.copy(photos = project.photos.filterNot { it.id == id }, updatedAt = store.clock.nowMillis()))
    }

    fun setWish(wish: String) = patch { it.copy(wish = wish) }

    fun setVoice(voice: VoiceLang) = patch { it.copy(voice = voice) }

    fun setCreative(mode: CreativeMode) = patch { it.copy(creative = mode) }

    fun setTiktok(enabled: Boolean) = patch { it.copy(tiktokShop = enabled) }

    fun generate() {
        viewModelScope.launch {
            val result = store.generateActive(stageHoldMs = 280)
            result.onSuccess { _openResultId.value = it.id }
        }
    }

    fun delete(id: String) = update { state ->
        photos.deleteProject(id)
        state.delete(id)
    }

    fun setTheme(mode: ThemeMode) = update { it.prefs(theme = mode) }

    fun setDefaultVoice(voice: VoiceLang) = update { it.prefs(defaultVoice = voice) }

    fun restoreDemo() = update { state ->
        photos.deleteAll()
        SeedProjects.populated(store.clock).copy(prefs = state.prefs)
    }

    fun clearLibrary() = update { state ->
        photos.deleteAll()
        StudioState(prefs = state.prefs).ensureDraft(store.clock)
    }

    private fun patch(transform: (Project) -> Project) = update { state ->
        val project = state.active() ?: return@update state
        state.upsert(transform(project).copy(updatedAt = store.clock.nowMillis()))
    }

    private fun update(transform: (StudioState) -> StudioState) {
        viewModelScope.launch { store.update(transform) }
    }

    class Factory(
        private val store: StudioStore,
        private val photos: PhotoStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StudioViewModel(store, photos) as T
    }
}
