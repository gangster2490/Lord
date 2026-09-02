package de.spardirekt.recipeveo

import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.recipeveo.domain.PhotoRef
import de.spardirekt.recipeveo.domain.StudioRules
import de.spardirekt.recipeveo.domain.StudioStore
import de.spardirekt.recipeveo.domain.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudioViewModel(app: RecipeVeoApp) : AndroidViewModel(app) {
    private val store: StudioStore = app.store
    private val photos: PhotoStore = app.photos

    val state = store.state

    private val _screen = MutableStateFlow<Screen>(Screen.Create)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { store.hydrate() }
    }

    fun go(screen: Screen) {
        _screen.value = screen
    }

    fun setWish(value: String) {
        viewModelScope.launch {
            store.update { state ->
                val project = state.active() ?: return@update state
                state.upsert(project.copy(wish = value, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = photos.import(uri)
                store.update { state ->
                    val project = state.active() ?: return@update state
                    if (project.photos.size >= StudioRules.MAX_PHOTOS) return@update state
                    state.upsert(
                        project.copy(
                            photos = project.photos + PhotoRef(newId(), path),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }.onFailure { _error.value = it.message ?: "Не удалось добавить фото." }
        }
    }

    fun removePhoto(id: String) {
        viewModelScope.launch {
            store.update { state ->
                val project = state.active() ?: return@update state
                val removed = project.photos.firstOrNull { it.id == id }
                if (removed != null) photos.delete(removed.uri)
                state.upsert(
                    project.copy(
                        photos = project.photos.filterNot { it.id == id },
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun generate() {
        viewModelScope.launch {
            val result = store.generateActive()
            result.fold(
                onSuccess = { project ->
                    store.newDraft()
                    _screen.value = Screen.Result(project.id)
                },
                onFailure = { _error.value = it.message ?: "Не удалось собрать промпт." },
            )
        }
    }

    fun openProject(id: String) {
        _screen.value = Screen.Result(id)
    }

    fun backFromResult() {
        _screen.value = Screen.Create
    }

    fun consumeError() {
        _error.value = null
    }

    sealed class Screen {
        data object Create : Screen()
        data object History : Screen()
        data class Result(val id: String) : Screen()
    }
}
