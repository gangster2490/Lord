package de.spardirekt.veoprompt.ultra.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.spardirekt.veoprompt.ultra.config.ModelConfig
import de.spardirekt.veoprompt.ultra.model.AppMode
import de.spardirekt.veoprompt.ultra.model.CreativeMode
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("veo_prompt_ultra_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val voice = stringPreferencesKey("default_voice")
        val mode = stringPreferencesKey("default_mode")
        val creative = stringPreferencesKey("default_creative")
        val tiktok = booleanPreferencesKey("tiktok_shop_mode")
        val model = stringPreferencesKey("openai_model")
        val debugLogs = booleanPreferencesKey("debug_logs")
        val lastProjectId = stringPreferencesKey("last_project_id")
        val lastSafeError = stringPreferencesKey("last_safe_error")
        val lastPipelineStage = stringPreferencesKey("last_pipeline_stage")
    }

    data class AppSettings(
        val defaultVoice: VoiceLanguage = VoiceLanguage.DE,
        val defaultMode: AppMode = AppMode.Simple,
        val defaultCreative: CreativeMode = CreativeMode.AUTO,
        val tiktokShopMode: Boolean = true,
        val model: String = ModelConfig.Profile.DEFAULT.modelId,
        val debugLogs: Boolean = false,
        val lastProjectId: String = "",
        val lastSafeError: String = "",
        val lastPipelineStage: String = ""
    )

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            defaultVoice = runCatching {
                VoiceLanguage.valueOf(p[Keys.voice] ?: VoiceLanguage.DE.name)
            }.getOrDefault(VoiceLanguage.DE),
            defaultMode = runCatching {
                AppMode.valueOf(p[Keys.mode] ?: AppMode.Simple.name)
            }.getOrDefault(AppMode.Simple),
            defaultCreative = CreativeMode.fromRaw(p[Keys.creative]),
            tiktokShopMode = p[Keys.tiktok] ?: true,
            model = ModelConfig.sanitize(p[Keys.model]),
            debugLogs = p[Keys.debugLogs] ?: false,
            lastProjectId = p[Keys.lastProjectId].orEmpty(),
            lastSafeError = p[Keys.lastSafeError].orEmpty(),
            lastPipelineStage = p[Keys.lastPipelineStage].orEmpty()
        )
    }

    suspend fun setVoice(v: VoiceLanguage) {
        context.settingsDataStore.edit { it[Keys.voice] = v.name }
    }

    suspend fun setMode(m: AppMode) {
        context.settingsDataStore.edit { it[Keys.mode] = m.name }
    }

    suspend fun setCreative(c: CreativeMode) {
        context.settingsDataStore.edit { it[Keys.creative] = c.name }
    }

    suspend fun setTiktok(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.tiktok] = on }
    }

    suspend fun setModel(model: String) {
        context.settingsDataStore.edit { it[Keys.model] = ModelConfig.sanitize(model) }
    }

    suspend fun setDebugLogs(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.debugLogs] = on }
    }

    suspend fun setLastProjectId(id: String) {
        context.settingsDataStore.edit { it[Keys.lastProjectId] = id }
    }

    suspend fun setLastSafeError(text: String) {
        context.settingsDataStore.edit { it[Keys.lastSafeError] = text }
    }

    suspend fun setLastPipelineStage(stage: String) {
        context.settingsDataStore.edit { it[Keys.lastPipelineStage] = stage }
    }
}
