package de.spardirekt.clipforge.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureApiKeyStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val app = context.applicationContext
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            app,
            "clipforge_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getKey(): String = prefs.getString(KEY, null)?.trim().orEmpty()

    fun hasKey(): Boolean = getKey().isNotBlank()

    fun saveKey(apiKey: String) {
        prefs.edit().putString(KEY, apiKey.trim()).apply()
    }

    fun removeKey() {
        prefs.edit().remove(KEY).apply()
    }

    fun maskedPreview(): String {
        val key = getKey()
        if (key.isEmpty()) return ""
        if (key.length <= 8) return "••••••••"
        return key.take(4) + "••••" + key.takeLast(4)
    }

    companion object {
        private const val KEY = "openai_api_key"
    }
}
