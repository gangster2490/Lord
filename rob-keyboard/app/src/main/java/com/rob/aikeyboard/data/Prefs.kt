package com.rob.aikeyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.rob.aikeyboard.emoji.EmojiIntensity
import com.rob.aikeyboard.text.Language

enum class Vibration(val label: String, val millis: Long, val amplitude: Int) {
    OFF("Выкл.", 0, 0), LIGHT("Слабая", 12, 60), MEDIUM("Средняя", 18, 140), STRONG("Сильная", 28, 255)
}

enum class AiProvider(val label: String) { OPENAI("ChatGPT"), ANTHROPIC("Claude") }

enum class TranslateEngine(val label: String) { OFFLINE("На устройстве (ML Kit)"), AI("Облако (AI-провайдер)") }

enum class SpaceSwipe(val label: String) { LANGUAGE("Смена языка"), CURSOR("Курсор") }

/** All user settings. Plain SharedPreferences; secrets live in [com.rob.aikeyboard.security.SecureStore]. */
class Prefs(context: Context) {
    val raw: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = raw.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) = raw.edit().putBoolean(key, v).apply()
    private fun str(key: String, def: String) = raw.getString(key, def) ?: def
    private fun setStr(key: String, v: String) = raw.edit().putString(key, v).apply()

    var enabledLanguages: List<Language>
        get() = str("languages", "ru,de,en").split(',').mapNotNull(Language::fromCode).ifEmpty { listOf(Language.RU) }
        set(v) = setStr("languages", v.joinToString(",") { it.code })

    var currentLanguage: Language
        get() = Language.fromCode(str("current_language", "ru"))?.takeIf { it in enabledLanguages } ?: enabledLanguages.first()
        set(v) = setStr("current_language", v.code)

    var autoDetectLanguage by BoolPref("auto_detect_language", true)
    var autoCorrect by BoolPref("auto_correct", true)
    var showSuggestions by BoolPref("show_suggestions", true)
    var autoCapitalize by BoolPref("auto_capitalize", true)
    var doubleSpacePeriod by BoolPref("double_space_period", true)
    var numberHints by BoolPref("number_hints", true)
    var soundOnKeypress by BoolPref("sound", false)
    var personalization by BoolPref("personalization", true)
    var autoEmoji by BoolPref("auto_emoji", false)
    var clipboardHistory by BoolPref("clipboard_history", true)
    var voicePreferOffline by BoolPref("voice_prefer_offline", false)
    var translateWifiOnly by BoolPref("translate_wifi_only", true)
    var quickFixWithAi by BoolPref("quick_fix_ai", false)

    var keyHeightScale: Float
        get() = raw.getFloat("key_height_scale", 1f)
        set(v) = raw.edit().putFloat("key_height_scale", v.coerceIn(0.75f, 1.4f)).apply()

    var soundVolume: Float
        get() = raw.getFloat("sound_volume", 0.4f)
        set(v) = raw.edit().putFloat("sound_volume", v).apply()

    var vibration: Vibration
        get() = enumOr(str("vibration", Vibration.LIGHT.name), Vibration.LIGHT)
        set(v) = setStr("vibration", v.name)

    var emojiIntensity: EmojiIntensity
        get() = enumOr(str("emoji_intensity", EmojiIntensity.NORMAL.name), EmojiIntensity.NORMAL)
        set(v) = setStr("emoji_intensity", v.name)

    var aiProvider: AiProvider
        get() = enumOr(str("ai_provider", AiProvider.ANTHROPIC.name), AiProvider.ANTHROPIC)
        set(v) = setStr("ai_provider", v.name)

    var translateEngine: TranslateEngine
        get() = enumOr(str("translate_engine", TranslateEngine.OFFLINE.name), TranslateEngine.OFFLINE)
        set(v) = setStr("translate_engine", v.name)

    var spaceSwipe: SpaceSwipe
        get() = enumOr(str("space_swipe", SpaceSwipe.LANGUAGE.name), SpaceSwipe.LANGUAGE)
        set(v) = setStr("space_swipe", v.name)

    var openAiModel by StrPref("openai_model", DEFAULT_OPENAI_MODEL)
    var anthropicModel by StrPref("anthropic_model", DEFAULT_ANTHROPIC_MODEL)
    var openAiBaseUrl by StrPref("openai_base_url", "https://api.openai.com")
    var anthropicBaseUrl by StrPref("anthropic_base_url", "https://api.anthropic.com")

    var emojiHistory: String?
        get() = raw.getString("emoji_history", null)
        set(v) = raw.edit().putString("emoji_history", v).apply()

    private inline fun <reified T : Enum<T>> enumOr(name: String, def: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(def)

    private inner class BoolPref(val key: String, val def: Boolean) {
        operator fun getValue(thisRef: Any?, p: kotlin.reflect.KProperty<*>) = bool(key, def)
        operator fun setValue(thisRef: Any?, p: kotlin.reflect.KProperty<*>, v: Boolean) = setBool(key, v)
    }

    private inner class StrPref(val key: String, val def: String) {
        operator fun getValue(thisRef: Any?, p: kotlin.reflect.KProperty<*>) = str(key, def).ifBlank { def }
        operator fun setValue(thisRef: Any?, p: kotlin.reflect.KProperty<*>, v: String) = setStr(key, v.trim())
    }

    fun resetAll() = raw.edit().clear().apply()

    companion object {
        const val DEFAULT_OPENAI_MODEL = "gpt-5-mini"
        const val DEFAULT_ANTHROPIC_MODEL = "claude-haiku-5-5"
    }
}
