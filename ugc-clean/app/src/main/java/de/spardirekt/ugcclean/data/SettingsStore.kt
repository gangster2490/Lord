package de.spardirekt.ugcclean.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import de.spardirekt.ugcclean.net.AiProviderId

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

    fun provider(): AiProviderId = AiProviderId.fromStored(prefs.getString(PROVIDER, null))

    fun setProvider(id: AiProviderId) {
        prefs.edit().putString(PROVIDER, id.name).apply()
    }

    fun apiKey(id: AiProviderId = provider()): String = prefs.getString(keyName(id), "").orEmpty()

    fun hasKey(id: AiProviderId = provider()): Boolean = apiKey(id).isNotBlank()

    fun saveKey(value: String, id: AiProviderId = provider()) {
        prefs.edit().putString(keyName(id), value.trim()).apply()
    }

    fun clearKey(id: AiProviderId = provider()) {
        prefs.edit().remove(keyName(id)).apply()
    }

    private fun keyName(id: AiProviderId): String = when (id) {
        AiProviderId.OPENAI -> KEY_OPENAI
        AiProviderId.GEMINI -> KEY_GEMINI
        AiProviderId.CLAUDE -> KEY_CLAUDE
    }

    companion object {
        private const val PROVIDER = "ai_provider"
        private const val KEY_OPENAI = "openai_api_key"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_CLAUDE = "claude_api_key"
    }
}
