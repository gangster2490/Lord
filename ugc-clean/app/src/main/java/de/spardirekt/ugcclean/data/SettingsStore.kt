package de.spardirekt.ugcclean.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ugc_clean_secure",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences("ugc_clean_settings", Context.MODE_PRIVATE)
    }

    fun apiKey(): String = prefs.getString(KEY, "").orEmpty()

    fun hasKey(): Boolean = apiKey().isNotBlank()

    fun saveKey(value: String) {
        prefs.edit().putString(KEY, value.trim()).apply()
    }

    fun clearKey() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "openai_api_key"
    }
}
