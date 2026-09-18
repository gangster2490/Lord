package com.rob.veocreator.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the Gemini API key using AES256-GCM encrypted SharedPreferences.
 * The key never touches Logcat and is never bundled in source.
 */
class SecureApiKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun deleteApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    companion object {
        private const val PREFS_FILE = "veo_creator_secure_prefs"
        private const val KEY_API_KEY = "gemini_api_key"

        /** Never log the real key; only this masked form. */
        fun mask(key: String): String {
            if (key.length <= 4) return "•".repeat(key.length)
            return "•".repeat(key.length - 4) + key.takeLast(4)
        }
    }
}
