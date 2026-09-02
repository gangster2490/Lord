package de.spardirekt.veoprompt.ultra.ui.result

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.veoprompt.ultra.VeoPromptUltraApp
import de.spardirekt.veoprompt.ultra.model.SafetyAudit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ResultViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VeoPromptUltraApp.instance.projectRepository
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(ResultViewState())
    val state: StateFlow<ResultViewState> = _state.asStateFlow()

    fun load(projectId: String) {
        viewModelScope.launch {
            val entity = repo.get(projectId) ?: return@launch
            val audit = runCatching {
                json.decodeFromString(SafetyAudit.serializer(), entity.safetyAuditJson)
            }.getOrDefault(SafetyAudit())
            val model = runCatching {
                json.decodeFromString(
                    de.spardirekt.veoprompt.ultra.model.ProductModel.serializer(),
                    entity.productModelJson
                )
            }.getOrNull()
            _state.value = ResultViewState(
                projectId = entity.id,
                veoPrompt = entity.veoPrompt,
                voiceover = entity.voiceover,
                title = entity.title,
                hashtags = repo.parseHashtags(entity),
                safetyRisk = audit.riskLevel,
                safetyItems = audit.items,
                safetyPolicyVersion = audit.policyVersion,
                tiktokShopMode = entity.tiktokShopMode,
                language = entity.voiceLanguage,
                creativeMode = entity.creativeMode,
                status = entity.status,
                expanded = false,
                productIdentity = model?.productIdentity.orEmpty(),
                visualSignature = model?.visualSignature.orEmpty(),
                appMode = entity.mode,
                loaded = true
            )
        }
    }

    fun toggleExpanded() {
        _state.update { it.copy(expanded = !it.expanded) }
    }

    fun copyVeoPrompt() {
        val text = _state.value.veoPrompt
        clipboard().setPrimaryClip(ClipData.newPlainText("veoPrompt", text))
    }

    fun copyPackage() {
        clipboard().setPrimaryClip(ClipData.newPlainText("package", _state.value.packageText()))
    }

    fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, _state.value.veoPrompt)
        }
        val chooser = Intent.createChooser(intent, "Поделиться")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(chooser)
    }

    private fun clipboard(): ClipboardManager =
        getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}
