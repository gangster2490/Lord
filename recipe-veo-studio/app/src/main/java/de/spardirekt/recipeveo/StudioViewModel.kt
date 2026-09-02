package de.spardirekt.recipeveo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.spardirekt.recipeveo.domain.Catalog
import de.spardirekt.recipeveo.domain.HomeMath
import de.spardirekt.recipeveo.domain.HomeSnapshot
import de.spardirekt.recipeveo.domain.Project
import de.spardirekt.recipeveo.domain.SeedStudio
import de.spardirekt.recipeveo.domain.ShotStyle
import de.spardirekt.recipeveo.domain.StudioRules
import de.spardirekt.recipeveo.domain.StudioState
import de.spardirekt.recipeveo.domain.StudioStore
import de.spardirekt.recipeveo.domain.ThemeMode
import de.spardirekt.recipeveo.domain.VoiceLang
import de.spardirekt.recipeveo.domain.ensureDraft
import de.spardirekt.recipeveo.domain.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudioViewModel(
    private val store: StudioStore,
    private val photos: PhotoStore,
) : ViewModel() {
    val ready: StateFlow<Boolean> = store.ready
    val state: StateFlow<StudioState> = store.state
    val home: StateFlow<HomeSnapshot> = store.state
        .map { HomeMath.snapshot(it, store.clock) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeMath.snapshot(store.state.value, store.clock),
        )
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

    fun newProject() = update { state ->
        val now = store.clock.nowMillis()
        val draft = Project(
            id = newId(),
            voice = state.prefs.defaultVoice,
            tiktokShop = state.prefs.tiktokShop,
            createdAt = now,
            updatedAt = now,
        )
        state.upsert(draft).copy(activeId = draft.id)
    }

    fun open(id: String) = update { it.open(id) }

    fun addPhotos(uris: List<String>) = update { state ->
        val project = state.active() ?: return@update state
        val existing = project.photos.map { it.uri }.toSet()
        val incoming = uris.filterNot { it in existing }
        if (incoming.isEmpty()) return@update state
        val added = photos.persist(project.id, incoming)
        val nextPhotos = (project.photos + added).take(StudioRules.MAX_PHOTOS)
        val productId = Catalog.fromPhotos(nextPhotos, project.productId)?.id
        state.upsert(project.copy(photos = nextPhotos, productId = productId, updatedAt = store.clock.nowMillis()))
    }

    fun addDemo(uri: String) = addPhotos(listOf(uri))

    fun removePhoto(id: String) = update { state ->
        val project = state.active() ?: return@update state
        photos.deletePhoto(project.id, id)
        val nextPhotos = project.photos.filterNot { it.id == id }
        state.upsert(
            project.copy(
                photos = nextPhotos,
                productId = Catalog.fromPhotos(nextPhotos, null)?.id,
                updatedAt = store.clock.nowMillis(),
            ),
        )
    }

    fun setWish(wish: String) = patch { it.copy(wish = wish) }

    fun setVoice(voice: VoiceLang) = patch { it.copy(voice = voice) }

    fun setStyle(style: ShotStyle) = patch { it.copy(style = style) }

    fun applyStyle(style: ShotStyle) {
        update { state ->
            val withDraft = state.ensureDraft(store.clock)
            val project = withDraft.active() ?: return@update withDraft
            withDraft.upsert(project.copy(style = style, updatedAt = store.clock.nowMillis()))
        }
    }

    fun setTiktok(enabled: Boolean) = patch { it.copy(tiktokShop = enabled) }

    fun generate() {
        viewModelScope.launch {
            val result = store.generateActive(stageHoldMs = 240)
            result.onSuccess { _openResultId.value = it.id }
        }
    }

    fun delete(id: String) = update { state ->
        photos.deleteProject(id)
        state.delete(id)
    }

    fun setName(name: String) = update { it.prefs(displayName = name) }

    fun setTheme(mode: ThemeMode) = update { it.prefs(theme = mode) }

    fun setDefaultVoice(voice: VoiceLang) = update { it.prefs(defaultVoice = voice) }

    fun setDefaultTiktok(enabled: Boolean) = update { it.prefs(tiktokShop = enabled) }

    fun restoreDemo() = update { state ->
        photos.deleteAll()
        SeedStudio.populated(store.clock, state.prefs)
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
