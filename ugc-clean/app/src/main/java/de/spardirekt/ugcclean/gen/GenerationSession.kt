package de.spardirekt.ugcclean.gen

import android.app.Application
import android.content.Intent
import android.os.Build
import de.spardirekt.ugcclean.data.ProjectStore
import de.spardirekt.ugcclean.data.SettingsStore
import de.spardirekt.ugcclean.image.ImageEncoder
import de.spardirekt.ugcclean.model.PipelineStage
import de.spardirekt.ugcclean.model.ProjectRecord
import de.spardirekt.ugcclean.model.ProjectStatus
import de.spardirekt.ugcclean.model.SpeechLanguage
import de.spardirekt.ugcclean.net.Keys
import de.spardirekt.ugcclean.net.ProviderClients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class GenerationSession(
    private val app: Application,
    private val projects: ProjectStore,
    private val settings: SettingsStore,
    private val clients: ProviderClients = ProviderClients(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _active = MutableStateFlow<ProjectRecord?>(null)
    val active: StateFlow<ProjectRecord?> = _active
    private var job: Job? = null

    fun isRunning(): Boolean = job?.isActive == true || _active.value?.status == ProjectStatus.RUNNING

    fun start(photoUris: List<String>, language: SpeechLanguage): String? {
        val key = settings.apiKey()
        val reason = StartGate.blockReason(photoUris.size, key.isNotBlank(), isRunning(), language)
        if (reason != null) return reason
        val now = System.currentTimeMillis()
        val record = ProjectRecord(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now,
            status = ProjectStatus.RUNNING,
            language = language,
            photoUris = photoUris,
            thumbnailUri = photoUris.firstOrNull(),
            stage = PipelineStage.PHOTOS,
            progressPercent = 5,
        )
        projects.save(record)
        _active.value = record
        startService()
        job = scope.launch {
            runCatching {
                val images = ImageEncoder.encodeAll(app, photoUris)
                if (images.size < StartGate.MIN_PHOTOS) {
                    throw PipelineException("Fotos konnten nicht gelesen werden.")
                }
                val attempts = ProviderClients.attemptOrder(settings.provider()) { settings.apiKey(it) }
                var last: Throwable? = null
                var used = if (Keys.isDemo(key)) "DEMO" else settings.provider().name
                var result: PipelineResult? = null
                for (attempt in attempts) {
                    val outcome = runCatching {
                        Pipeline(liveClient = clients.forKey(attempt.provider, attempt.key), demoClient = clients.demo)
                            .run(attempt.key, images, language) { stage, percent ->
                                update { it.copy(stage = stage, progressPercent = percent, updatedAt = System.currentTimeMillis()) }
                            }
                    }
                    if (outcome.isSuccess) {
                        result = outcome.getOrThrow()
                        used = if (Keys.isDemo(attempt.key)) "DEMO" else attempt.provider.name
                        last = null
                        break
                    }
                    last = outcome.exceptionOrNull()
                }
                if (result == null) throw last ?: PipelineException("Anfrage fehlgeschlagen.")
                result to used
            }.onSuccess { (result, used) ->
                update {
                    it.copy(
                        status = ProjectStatus.READY,
                        stage = PipelineStage.DONE,
                        progressPercent = 100,
                        plan = result.plan,
                        veoPrompt = result.veoPrompt,
                        copyPack = result.copyPack,
                        errorMessage = null,
                        aiProvider = used,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            }.onFailure { err ->
                update {
                    it.copy(
                        status = ProjectStatus.ERROR,
                        errorMessage = err.message ?: "Unbekannter Fehler",
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            }
            stopService()
            job = null
        }
        return null
    }

    fun retry(id: String): String? {
        val existing = projects.get(id) ?: return "Projekt nicht gefunden."
        if (isRunning()) return "Ein Lauf läuft bereits."
        return start(existing.photoUris, existing.language)
    }

    private fun update(transform: (ProjectRecord) -> ProjectRecord) {
        val current = _active.value ?: return
        val next = transform(current)
        projects.save(next)
        _active.value = next
    }

    private fun startService() {
        val intent = Intent(app, PipelineForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    private fun stopService() {
        app.stopService(Intent(app, PipelineForegroundService::class.java))
    }
}
