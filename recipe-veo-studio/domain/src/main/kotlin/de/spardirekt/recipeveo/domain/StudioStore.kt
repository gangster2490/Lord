package de.spardirekt.recipeveo.domain

import kotlinx.coroutines.delay
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
                SeedStudio.populated(clock).also { persist.write(it) }
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

    suspend fun generateActive(stageHoldMs: Long = 0): Result<Project> {
        mutex.withLock {
            val current = _state.value
            val project = current.active() ?: return Result.failure(IllegalStateException("Нет активного проекта."))
            if (!StudioRules.canGenerate(project)) {
                return Result.failure(IllegalStateException("Добавьте фото товара."))
            }
            val started = project.copy(
                status = ProjectStatus.Generating,
                stage = GenerationStage.PHOTO_ANALYSIS,
                error = "",
                updatedAt = clock.nowMillis(),
            )
            write(current.upsert(started))
            listOf(
                GenerationStage.PRODUCT_MODEL,
                GenerationStage.CREATIVE_DIRECTOR,
                GenerationStage.FINAL_PROMPT,
            ).forEach { stage ->
                if (stageHoldMs > 0) delay(stageHoldMs)
                write(_state.value.upsert(started.copy(stage = stage, updatedAt = clock.nowMillis())))
            }
            if (stageHoldMs > 0) delay(stageHoldMs)
            val pkg = runCatching { VeoRecipe.fromProject(started, clock.nowMillis()) }.getOrElse { err ->
                val failed = started.copy(
                    status = ProjectStatus.Error,
                    stage = GenerationStage.FAILED,
                    error = err.message ?: "Не удалось собрать промпт.",
                    updatedAt = clock.nowMillis(),
                )
                write(_state.value.upsert(failed))
                return Result.failure(err)
            }
            val ready = started.copy(
                title = pkg.title,
                status = ProjectStatus.Ready,
                stage = GenerationStage.DONE,
                result = pkg,
                updatedAt = clock.nowMillis(),
            )
            write(_state.value.upsert(ready))
            return Result.success(ready)
        }
    }

    private suspend fun write(next: StudioState) {
        persist.write(next)
        _state.value = next
    }
}
