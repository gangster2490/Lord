package de.spardirekt.clipforge.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.spardirekt.clipforge.data.model.AdFormula
import de.spardirekt.clipforge.data.model.AdLanguage
import de.spardirekt.clipforge.data.model.AdLength
import de.spardirekt.clipforge.data.model.HistoryEntry
import de.spardirekt.clipforge.data.model.Platform
import de.spardirekt.clipforge.data.model.VisualStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "clipforge_settings")
private val Context.historyStore: DataStore<Preferences> by preferencesDataStore(name = "clipforge_history")

class SettingsStore(private val context: Context) {

    val platformId: Flow<String> = context.settingsStore.data.map { it[PLATFORM] ?: Platform.TIKTOK_SHOP.id }
    val lengthSeconds: Flow<Int> = context.settingsStore.data.map {
        it[LENGTH]?.toIntOrNull() ?: AdLength.EIGHT.seconds
    }
    val formulaId: Flow<String> = context.settingsStore.data.map { it[FORMULA] ?: AdFormula.HOOK_DEMO_CTA.id }
    val styleId: Flow<String> = context.settingsStore.data.map { it[STYLE] ?: VisualStyle.CINEMATIC.id }
    val languageId: Flow<String> = context.settingsStore.data.map { it[LANGUAGE] ?: AdLanguage.RU.id }

    suspend fun setPlatform(value: Platform) {
        context.settingsStore.edit { it[PLATFORM] = value.id }
    }

    suspend fun setLength(value: AdLength) {
        context.settingsStore.edit { it[LENGTH] = value.seconds.toString() }
    }

    suspend fun setFormula(value: AdFormula) {
        context.settingsStore.edit { it[FORMULA] = value.id }
    }

    suspend fun setStyle(value: VisualStyle) {
        context.settingsStore.edit { it[STYLE] = value.id }
    }

    suspend fun setLanguage(value: AdLanguage) {
        context.settingsStore.edit { it[LANGUAGE] = value.id }
    }

    companion object {
        private val PLATFORM = stringPreferencesKey("platform")
        private val LENGTH = stringPreferencesKey("length_seconds")
        private val FORMULA = stringPreferencesKey("formula")
        private val STYLE = stringPreferencesKey("style")
        private val LANGUAGE = stringPreferencesKey("language")
    }
}

class HistoryStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val entries: Flow<List<HistoryEntry>> = context.historyStore.data.map { prefs ->
        val raw = prefs[ENTRIES].orEmpty()
        if (raw.isBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<HistoryEntry>>(raw) }.getOrDefault(emptyList())
        }
    }

    suspend fun upsert(entry: HistoryEntry) {
        context.historyStore.edit { prefs ->
            val current = prefs[ENTRIES].orEmpty().let { raw ->
                if (raw.isBlank()) {
                    emptyList()
                } else {
                    runCatching { json.decodeFromString<List<HistoryEntry>>(raw) }.getOrDefault(emptyList())
                }
            }
            val next = (listOf(entry) + current.filterNot { it.id == entry.id }).take(MAX)
            prefs[ENTRIES] = json.encodeToString(next)
        }
    }

    suspend fun remove(id: String) {
        context.historyStore.edit { prefs ->
            val current = prefs[ENTRIES].orEmpty().let { raw ->
                if (raw.isBlank()) emptyList()
                else runCatching { json.decodeFromString<List<HistoryEntry>>(raw) }.getOrDefault(emptyList())
            }
            prefs[ENTRIES] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    suspend fun clear() {
        context.historyStore.edit { it[ENTRIES] = "[]" }
    }

    companion object {
        const val MAX = 40
        private val ENTRIES = stringPreferencesKey("entries_json")
    }
}
