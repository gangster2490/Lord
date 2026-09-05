package de.spardirekt.ugcagent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        val app = context.applicationContext
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            app,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    fun getApiKey(): String? = prefs.getString(KEY_API, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun saveApiKey(value: String) {
        prefs.edit().putString(KEY_API, value.trim()).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API).apply()
    }

    fun maskedApiKey(): String {
        val key = getApiKey() ?: return ""
        if (key.length <= 8) return "••••••••"
        return key.take(4) + "••••" + key.takeLast(4)
    }

    fun getLanguage(): String {
        val value = prefs.getString(KEY_LANG, "de") ?: "de"
        return if (value == "ru") "ru" else "de"
    }

    fun setLanguage(lang: String) {
        val normalized = if (lang == "ru") "ru" else "de"
        prefs.edit().putString(KEY_LANG, normalized).apply()
    }

    fun getLastSceneKey(): String? = prefs.getString(KEY_LAST_SCENE, null)

    fun setLastSceneKey(key: String?) {
        prefs.edit().putString(KEY_LAST_SCENE, key).apply()
    }

    companion object {
        private const val PREFS_NAME = "ugc_agent_secure"
        private const val KEY_API = "openai_api_key"
        private const val KEY_LANG = "ui_language"
        private const val KEY_LAST_SCENE = "last_scene_key"
    }
}
