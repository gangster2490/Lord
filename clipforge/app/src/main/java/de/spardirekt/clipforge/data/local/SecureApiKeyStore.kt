package de.spardirekt.clipforge.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureApiKeyStore(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

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
        private const val ENCRYPTED_FILE = "clipforge_secure"
        private const val FALLBACK_FILE = "clipforge_secure_fallback"

        private fun createPrefs(app: Context): SharedPreferences {
            return try {
                encryptedPrefs(app)
            } catch (_: Exception) {
                try {
                    app.deleteSharedPreferences(ENCRYPTED_FILE)
                    encryptedPrefs(app)
                } catch (_: Exception) {
                    app.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
                }
            }
        }

        private fun encryptedPrefs(app: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                app,
                ENCRYPTED_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
