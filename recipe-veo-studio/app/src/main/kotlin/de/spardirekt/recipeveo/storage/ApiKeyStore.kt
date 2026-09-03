package de.spardirekt.recipeveo.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiKeyStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "recipe_veo_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getKey(): String? = prefs.getString("openai", null)?.ifBlank { null }
    fun setKey(key: String) = prefs.edit().putString("openai", key.trim()).apply()
    fun clearKey() = prefs.edit().remove("openai").apply()
    fun hasKey(): Boolean = !getKey().isNullOrBlank()
}
