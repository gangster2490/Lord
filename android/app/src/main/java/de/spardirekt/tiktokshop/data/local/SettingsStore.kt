package de.spardirekt.tiktokshop.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tiktok_shop_settings")

class SettingsStore(private val context: Context) {

    val proxyUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PROXY_URL] ?: DEFAULT_PROXY
    }

    suspend fun setProxyUrl(value: String) {
        context.dataStore.edit { it[PROXY_URL] = value }
    }

    companion object {
        const val DEFAULT_PROXY = "http://10.0.2.2:3001"
        private val PROXY_URL = stringPreferencesKey("proxy_url")
    }
}
