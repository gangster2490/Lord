package de.spardirekt.veoprompt.ultra.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.veoprompt.ultra.VeoPromptUltraApp
import de.spardirekt.veoprompt.ultra.compliance.SevenDayPromotionalRiskAnalyzer
import de.spardirekt.veoprompt.ultra.data.db.ProjectEntity
import de.spardirekt.veoprompt.ultra.model.ProjectStatus
import de.spardirekt.veoprompt.ultra.model.SafetyAudit
import de.spardirekt.veoprompt.ultra.storage.ImageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VeoPromptUltraApp.instance.projectRepository
    private val json = Json { ignoreUnknownKeys = true }

    val projects: StateFlow<List<ProjectEntity>> = repo.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sevenDayRisk: StateFlow<SevenDayPromotionalRiskAnalyzer.Report> = repo.observeHistory()
        .map { list ->
            SevenDayPromotionalRiskAnalyzer.analyze(
                snapshots = list.map(::snapshot),
                nowMs = System.currentTimeMillis()
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SevenDayPromotionalRiskAnalyzer.Report()
        )

    fun delete(id: String) {
        viewModelScope.launch {
            ImageStore.deleteProject(getApplication(), id)
            repo.delete(id)
        }
    }

    fun duplicate(entity: ProjectEntity, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val copy = repo.duplicate(entity)
            val images = ImageStore.copyProjectImages(
                getApplication(),
                repo.parseImages(entity),
                copy.id
            )
            repo.attachCopiedImages(copy, images)
            onCreated(copy.id)
        }
    }

    fun newVersion(entity: ProjectEntity, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val copy = repo.newVersion(entity)
            val images = ImageStore.copyProjectImages(
                getApplication(),
                repo.parseImages(entity),
                copy.id
            )
            repo.attachCopiedImages(copy, images)
            onCreated(copy.id)
        }
    }

    private fun snapshot(entity: ProjectEntity): SevenDayPromotionalRiskAnalyzer.Snapshot {
        val audit = runCatching {
            json.decodeFromString(SafetyAudit.serializer(), entity.safetyAuditJson)
        }.getOrDefault(SafetyAudit())
        return SevenDayPromotionalRiskAnalyzer.Snapshot(
            id = entity.id,
            updatedAt = entity.updatedAt,
            status = entity.status,
            hasPackage = entity.veoPrompt.isNotBlank(),
            safetyRisk = audit.riskLevel,
            auditItems = audit.items,
            aigcVerdict = audit.aigc.verdict,
            aigcShopPublishSafe = audit.aigc.shopPublishSafe
        )
    }

    fun openTarget(entity: ProjectEntity): OpenTarget {
        return when (entity.status) {
            ProjectStatus.Ready.name -> OpenTarget.Result(entity.id)
            ProjectStatus.Failed.name, ProjectStatus.Generating.name -> OpenTarget.Generation(entity.id)
            else -> OpenTarget.Create(entity.id)
        }
    }

    sealed class OpenTarget {
        data class Result(val id: String) : OpenTarget()
        data class Generation(val id: String) : OpenTarget()
        data class Create(val id: String) : OpenTarget()
    }
}
