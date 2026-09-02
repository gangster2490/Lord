package de.spardirekt.svoe.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface LifePersist {
    suspend fun read(): LifeState?
    suspend fun write(state: LifeState)
}

class FileLifePersist(
    private val file: File,
    private val json: Json = LifeJson,
) : LifePersist {
    override suspend fun read(): LifeState? {
        if (!file.exists() || file.length() == 0L) return null
        val text = file.readText()
        if (text.isBlank()) return null
        return json.decodeFromString(LifeState.serializer(), text)
    }

    override suspend fun write(state: LifeState) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(LifeState.serializer(), state))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }
}

class MemoryPersist(
    initial: LifeState? = null,
) : LifePersist {
    private var stored: LifeState? = initial

    override suspend fun read(): LifeState? = stored

    override suspend fun write(state: LifeState) {
        stored = state
    }
}

val LifeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

class LifeStore(
    private val persist: LifePersist,
    val clock: AppClock = SystemAppClock(),
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LifeState.Empty)
    val state: StateFlow<LifeState> = _state.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    suspend fun hydrate() {
        mutex.withLock {
            val loaded = persist.read()
            val next = if (loaded == null) {
                SeedData.populated(clock, Prefs(onboardingDone = true, currencyCode = "EUR")).also { persist.write(it) }
            } else {
                loaded
            }
            _state.value = next
            _ready.value = true
        }
    }

    suspend fun update(transform: (LifeState) -> LifeState): LifeState {
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
