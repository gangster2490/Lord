package de.spardirekt.recipeveo.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface StudioPersist {
    suspend fun read(): StudioState?
    suspend fun write(state: StudioState)
}

class FileStudioPersist(private val file: File) : StudioPersist {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    override suspend fun read(): StudioState? {
        if (!file.exists() || file.length() == 0L) return null
        val text = file.readText()
        if (text.isBlank()) return null
        return json.decodeFromString(StudioState.serializer(), text)
    }
    override suspend fun write(state: StudioState) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(StudioState.serializer(), state))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }
}

class MemoryPersist(private var stored: StudioState? = null) : StudioPersist {
    override suspend fun read() = stored
    override suspend fun write(state: StudioState) { stored = state }
}

class StudioStore(private val persist: StudioPersist) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(StudioState.Empty)
    val state: StateFlow<StudioState> = _state.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    suspend fun hydrate() {
        mutex.withLock {
            val loaded = persist.read()
            val next = loaded ?: emptyDraft(System.currentTimeMillis()).also { persist.write(it) }
            _state.value = next
            _ready.value = true
        }
    }

    suspend fun update(transform: (StudioState) -> StudioState): StudioState = mutex.withLock {
        val next = transform(_state.value)
        if (next != _state.value) {
            persist.write(next)
            _state.value = next
        }
        _state.value
    }

    suspend fun generateActive(now: Long = System.currentTimeMillis()): Result<Project> = mutex.withLock {
        val current = _state.value
        val project = current.active() ?: return Result.failure(IllegalStateException("Нет проекта."))
        if (!StudioRules.canGenerate(project)) {
            return Result.failure(IllegalStateException("Добавьте фото товара."))
        }
        val prompt = runCatching { VeoPrompt.compile(project.photos.size, project.wish, now) }
            .getOrElse { return Result.failure(it) }
        val ready = project.copy(prompt = prompt, updatedAt = now)
        write(current.upsert(ready).copy(activeId = ready.id))
        Result.success(ready)
    }

    suspend fun newDraft(now: Long = System.currentTimeMillis()) = update { state ->
        val draft = Project(id = newId(), createdAt = now, updatedAt = now)
        state.upsert(draft).copy(activeId = draft.id)
    }

    private suspend fun write(next: StudioState) {
        persist.write(next)
        _state.value = next
    }

    companion object {
        fun emptyDraft(now: Long) = StudioState(
            projects = listOf(Project(id = newId(), createdAt = now, updatedAt = now)),
        ).let { it.copy(activeId = it.projects.first().id) }
    }
}
