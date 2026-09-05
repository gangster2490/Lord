package de.spardirekt.ugcagent.v3.data

import android.content.Context
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ugc_v3_settings", Context.MODE_PRIVATE)

    var appLanguage: String
        get() = prefs.getString("appLanguage", "de") ?: "de"
        set(value) { prefs.edit().putString("appLanguage", value).apply() }

    var speechLanguage: String
        get() = prefs.getString("speechLanguage", "DEUTSCH") ?: "DEUTSCH"
        set(value) { prefs.edit().putString("speechLanguage", value).apply() }

    var captionLanguage: String
        get() = prefs.getString("captionLanguage", "DEUTSCH") ?: "DEUTSCH"
        set(value) { prefs.edit().putString("captionLanguage", value).apply() }

    var targetGenerator: String
        get() = prefs.getString("targetGenerator", "VEO") ?: "VEO"
        set(value) { prefs.edit().putString("targetGenerator", value).apply() }

    var strictProductLock: Boolean
        get() = prefs.getBoolean("strictProductLock", true)
        set(value) { prefs.edit().putBoolean("strictProductLock", value).apply() }

    var provider: String
        get() = prefs.getString("provider", "OPENAI") ?: "OPENAI"
        set(value) { prefs.edit().putString("provider", value).apply() }

    var privacyAccepted: Boolean
        get() = prefs.getBoolean("privacyAccepted", false)
        set(value) { prefs.edit().putBoolean("privacyAccepted", value).apply() }

    fun toJson(): JSONObject = JSONObject()
        .put("appLanguage", appLanguage)
        .put("speechLanguage", speechLanguage)
        .put("captionLanguage", captionLanguage)
        .put("targetGenerator", targetGenerator)
        .put("strictProductLock", strictProductLock)
        .put("provider", provider)
        .put("privacyAccepted", privacyAccepted)
        .put("outputLanguage", speechLanguage)
        .put("policyVersion", de.spardirekt.ugcagent.v3.compliance.TikTokShopPolicyConfig.VERSION)
        .put("policyUpdated", de.spardirekt.ugcagent.v3.compliance.TikTokShopPolicyConfig.LAST_UPDATED)
}
