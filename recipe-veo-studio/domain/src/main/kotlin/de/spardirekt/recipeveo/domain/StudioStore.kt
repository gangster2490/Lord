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

class FileStudioPersist(
    private val file: File,
    private val json: Json = StudioJson,
) : StudioPersist {
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

class MemoryPersist(initial: StudioState? = null) : StudioPersist {
    private var stored: StudioState? = initial
    override suspend fun read(): StudioState? = stored
    override suspend fun write(state: StudioState) {
        stored = state
    }
}

val StudioJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

class StudioStore(
    private val persist: StudioPersist,
    val clock: AppClock = SystemAppClock(),
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(StudioState.Empty)
    val state: StateFlow<StudioState> = _state.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    suspend fun hydrate() {
        mutex.withLock {
            val loaded = persist.read()
            val next = if (loaded == null) {
                SeedLibrary.populated(clock).also { persist.write(it) }
            } else {
                loaded
            }
            _state.value = next
            _ready.value = true
        }
    }

    suspend fun update(transform: (StudioState) -> StudioState): StudioState {
        mutex.withLock {
            val next = transform(_state.value)
            if (next != _state.value) {
                persist.write(next)
                _state.value = next
            }
            return _state.value
        }
    }
}
