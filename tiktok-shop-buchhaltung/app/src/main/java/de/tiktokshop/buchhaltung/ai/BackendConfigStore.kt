package de.tiktokshop.buchhaltung.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiConfigDataStore by preferencesDataStore(name = "ai_vision_config")

/**
 * Speichert NUR die URL des eigenen Backend-Proxys und optional ein Bearer-Token für ihn -
 * niemals einen AI-Anbieter-API-Key. Die URL liegt in DataStore (unkritisch), das Token in
 * [EncryptedSharedPreferences] (Android Keystore-gestützt), falls der Proxy eines verlangt.
 */
class BackendConfigStore(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("ai_vision_base_url")
    }

    val baseUrl: Flow<String?> = context.aiConfigDataStore.data.map { it[Keys.BASE_URL] }

    suspend fun setBaseUrl(url: String?) {
        context.aiConfigDataStore.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(Keys.BASE_URL) else prefs[Keys.BASE_URL] = url.trim()
        }
    }

    suspend fun currentBaseUrl(): String? = baseUrl.first()

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ai_vision_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun backendToken(): String? = encryptedPrefs.getString(KEY_TOKEN, null)

    fun setBackendToken(token: String?) {
        encryptedPrefs.edit().apply {
            if (token.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, token.trim())
        }.apply()
    }

    private companion object {
        const val KEY_TOKEN = "backend_token"
    }
}
