package de.spardirekt.recipeveo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.spardirekt.recipeveo.domain.AppClock
import de.spardirekt.recipeveo.domain.Recipe
import de.spardirekt.recipeveo.domain.RecipeKind
import de.spardirekt.recipeveo.domain.SeedLibrary
import de.spardirekt.recipeveo.domain.StudioState
import de.spardirekt.recipeveo.domain.StudioStore
import de.spardirekt.recipeveo.domain.ThemeMode
import de.spardirekt.recipeveo.domain.VoiceLang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StudioViewModel(
    private val store: StudioStore,
) : ViewModel() {
    val clock: AppClock = store.clock
    val ready: StateFlow<Boolean> = store.ready
    val state: StateFlow<StudioState> = store.state
    private val _openedId = MutableStateFlow<String?>(null)
    val openedId: StateFlow<String?> = _openedId

    init {
        viewModelScope.launch { store.hydrate() }
    }

    fun open(id: String?) {
        _openedId.value = id
    }

    fun createAndOpen() {
        viewModelScope.launch {
            var id = ""
            store.update { current ->
                val (next, recipe) = current.createBlank(clock)
                id = recipe.id
                next
            }
            _openedId.value = id
        }
    }

    fun save(recipe: Recipe) = update { it.upsert(recipe.copy(updatedAt = clock.nowMillis())) }

    fun saveAndCompile(recipe: Recipe) = update {
        it.upsert(recipe.copy(updatedAt = clock.nowMillis())).compile(recipe.id, clock)
    }

    fun delete(id: String) {
        if (_openedId.value == id) _openedId.value = null
        update { it.delete(id) }
    }

    fun setTheme(mode: ThemeMode) = update { it.updatePrefs(theme = mode) }

    fun setDefaultVoice(voice: VoiceLang) = update { it.updatePrefs(defaultVoice = voice) }

    fun restoreLibrary() = update { SeedLibrary.populated(clock).copy(prefs = it.prefs) }

    fun clearLibrary() = update { StudioState(prefs = it.prefs) }

    fun recipe(id: String?): Recipe? = id?.let { state.value.recipe(it) }

    fun visible(query: String, kind: RecipeKind?): List<Recipe> = state.value.visible(query, kind)

    private fun update(transform: (StudioState) -> StudioState) {
        viewModelScope.launch { store.update(transform) }
    }

    class Factory(private val store: StudioStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StudioViewModel(store) as T
    }
}
