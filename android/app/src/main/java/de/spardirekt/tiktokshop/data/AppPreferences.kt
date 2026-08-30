package de.spardirekt.tiktokshop.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "tiktok_shop_prefs")

class AppPreferences(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val snapshot: Flow<PrefsSnapshot> = context.dataStore.data.map { prefs ->
        PrefsSnapshot(
            anthropicKey = prefs[ANTHROPIC_KEY].orEmpty(),
            openAiKey = prefs[OPENAI_KEY].orEmpty(),
            proxyUrl = prefs[PROXY_URL]?.ifBlank { null } ?: ClaudeApiClient.DEFAULT_PROXY,
            videoStyle = prefs[VIDEO_STYLE] ?: CreatorOptions.videoStyles.first(),
            tone = prefs[TONE] ?: CreatorOptions.tones.first(),
            analysisModel = prefs[ANALYSIS_MODEL] ?: "gpt-4o",
            imageModel = prefs[IMAGE_MODEL] ?: "dall-e-3",
            veoHistory = decodeHistory(prefs[VEO_HISTORY].orEmpty()),
        )
    }

    suspend fun current(): PrefsSnapshot = snapshot.first()

    suspend fun setAnthropicKey(value: String) = set(ANTHROPIC_KEY, value)
    suspend fun setOpenAiKey(value: String) = set(OPENAI_KEY, value)
    suspend fun setProxyUrl(value: String) = set(PROXY_URL, value)
    suspend fun setVideoStyle(value: String) = set(VIDEO_STYLE, value)
    suspend fun setTone(value: String) = set(TONE, value)
    suspend fun setAnalysisModel(value: String) = set(ANALYSIS_MODEL, value)
    suspend fun setImageModel(value: String) = set(IMAGE_MODEL, value)

    suspend fun setHistory(entries: List<VeoHistoryEntry>) {
        context.dataStore.edit { it[VEO_HISTORY] = json.encodeToString(entries) }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it.remove(VEO_HISTORY) }
    }

    private suspend fun set(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private fun decodeHistory(raw: String): List<VeoHistoryEntry> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<VeoHistoryEntry>>(raw) }.getOrDefault(emptyList())
    }

    companion object {
        private val ANTHROPIC_KEY = stringPreferencesKey("anthropic_key")
        private val OPENAI_KEY = stringPreferencesKey("openai_key")
        private val PROXY_URL = stringPreferencesKey("proxy_url")
        private val VIDEO_STYLE = stringPreferencesKey("video_style")
        private val TONE = stringPreferencesKey("tone")
        private val ANALYSIS_MODEL = stringPreferencesKey("analysis_model")
        private val IMAGE_MODEL = stringPreferencesKey("image_model")
        private val VEO_HISTORY = stringPreferencesKey("veo_history")
    }
}

data class PrefsSnapshot(
    val anthropicKey: String,
    val openAiKey: String,
    val proxyUrl: String,
    val videoStyle: String,
    val tone: String,
    val analysisModel: String,
    val imageModel: String,
    val veoHistory: List<VeoHistoryEntry>,
)
