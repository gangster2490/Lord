package de.spardirekt.veoprompt.ultra.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.veoprompt.ultra.BuildConfig
import de.spardirekt.veoprompt.ultra.VeoPromptUltraApp
import de.spardirekt.veoprompt.ultra.config.ModelConfig
import de.spardirekt.veoprompt.ultra.data.db.AppDatabase
import de.spardirekt.veoprompt.ultra.diagnostics.DebugLog
import de.spardirekt.veoprompt.ultra.model.CreativeMode
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import de.spardirekt.veoprompt.ultra.storage.ImageStore
import de.spardirekt.veoprompt.ultra.storage.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiagnosticsState(
    val appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    val pipelineStage: String = "",
    val lastSafeError: String = "",
    val modelId: String = ModelConfig.Profile.DEFAULT.modelId,
    val modelLabel: String = ModelConfig.Profile.DEFAULT.label,
    val databaseStatus: String = "unknown",
    val hasApiKey: Boolean = false,
    val compliancePolicy: String = de.spardirekt.veoprompt.ultra.compliance.TikTokShopPolicy.VERSION
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = VeoPromptUltraApp.instance.settingsStore
    private val apiKeys = VeoPromptUltraApp.instance.apiKeyStore
    private val repo = VeoPromptUltraApp.instance.projectRepository
    private val openAi = VeoPromptUltraApp.instance.openAiClient

    val settings: StateFlow<SettingsStore.AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.AppSettings())

    private val _apiInput = MutableStateFlow("")
    val apiInput: StateFlow<String> = _apiInput.asStateFlow()

    private val _testMessage = MutableStateFlow("")
    val testMessage: StateFlow<String> = _testMessage.asStateFlow()

    private val _diagnostics = MutableStateFlow(DiagnosticsState())
    val diagnostics: StateFlow<DiagnosticsState> = _diagnostics.asStateFlow()

    init {
        refreshDiagnostics()
    }

    fun setApiInput(v: String) { _apiInput.value = v }

    fun addOrReplaceKey() {
        val key = _apiInput.value.trim()
        if (key.isBlank()) return
        apiKeys.saveKey(key)
        _apiInput.value = ""
        _testMessage.value = "Ключ сохранён"
        refreshDiagnostics()
    }

    fun removeKey() {
        apiKeys.removeKey()
        _testMessage.value = "Ключ удалён"
        refreshDiagnostics()
    }

    fun testKey() {
        viewModelScope.launch {
            val key = apiKeys.getKey()
            if (key.isNullOrBlank()) {
                _testMessage.value = "Сначала добавьте ключ"
                return@launch
            }
            _testMessage.value = "Проверка…"
            val result = withContext(Dispatchers.IO) { openAi.testConnection(key) }
            _testMessage.value = result.fold(
                onSuccess = { it },
                onFailure = { "Ошибка: ${it.message ?: "неизвестно"}" }
            )
        }
    }

    fun setVoice(v: VoiceLanguage) {
        viewModelScope.launch { settingsStore.setVoice(v) }
    }

    fun setCreative(c: CreativeMode) {
        viewModelScope.launch { settingsStore.setCreative(c) }
    }

    fun setTiktok(on: Boolean) {
        viewModelScope.launch { settingsStore.setTiktok(on) }
    }

    fun setModel(profile: ModelConfig.Profile) {
        viewModelScope.launch { settingsStore.setModel(profile.modelId) }
    }

    fun clearData() {
        viewModelScope.launch {
            ImageStore.deleteAll(getApplication())
            repo.clearAll()
            refreshDiagnostics()
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            val sett = settings.value
            val count = runCatching { repo.count() }.getOrDefault(-1)
            _diagnostics.update {
                DiagnosticsState(
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    pipelineStage = sett.lastPipelineStage.ifBlank { "—" },
                    lastSafeError = sett.lastSafeError.ifBlank { DebugLog.lastSafeError.ifBlank { "—" } },
                    modelId = sett.model,
                    modelLabel = ModelConfig.profile(sett.model).label,
                    databaseStatus = if (count >= 0) "OK · ${AppDatabase.NAME} · $count проектов" else "error",
                    hasApiKey = apiKeys.hasKey(),
                    compliancePolicy = de.spardirekt.veoprompt.ultra.compliance.TikTokShopPolicy.VERSION
                )
            }
        }
    }

    fun maskedKey(): String = apiKeys.maskedPreview()
}
