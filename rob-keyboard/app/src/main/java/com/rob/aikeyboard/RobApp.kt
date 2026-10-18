package com.rob.aikeyboard

import android.app.Application
import android.graphics.Paint
import com.rob.aikeyboard.ai.AiClient
import com.rob.aikeyboard.data.ClipboardStore
import com.rob.aikeyboard.data.Prefs
import com.rob.aikeyboard.data.UserModelStore
import com.rob.aikeyboard.emoji.AutoEmoji
import com.rob.aikeyboard.emoji.EmojiCatalog
import com.rob.aikeyboard.emoji.EmojiHistory
import com.rob.aikeyboard.security.SecureStore
import com.rob.aikeyboard.text.BigramModel
import com.rob.aikeyboard.text.Dictionary
import com.rob.aikeyboard.text.Language
import com.rob.aikeyboard.text.LanguageDetector
import com.rob.aikeyboard.text.SuggestionEngine
import com.rob.aikeyboard.translate.TranslatorService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class RobApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var secure: SecureStore
        private set
    lateinit var userModel: UserModelStore
        private set
    lateinit var clipboard: ClipboardStore
        private set
    lateinit var engine: SuggestionEngine
        private set
    lateinit var detector: LanguageDetector
        private set
    lateinit var ai: AiClient
        private set
    lateinit var translator: TranslatorService
        private set
    val autoEmoji = AutoEmoji()
    val io = Executors.newSingleThreadExecutor()

    private val dictionaries = ConcurrentHashMap<Language, Dictionary>()
    private val bigrams = ConcurrentHashMap<Language, BigramModel>()

    @Volatile
    var emojiCatalog: EmojiCatalog? = null
        private set
    lateinit var emojiHistory: EmojiHistory
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        secure = SecureStore(this)
        userModel = UserModelStore(this, secure)
        clipboard = ClipboardStore(this, secure)
        engine = SuggestionEngine({ dictionaries[it] }, userModel.model, { bigrams[it] })
        detector = LanguageDetector { dictionaries[it] }
        ai = AiClient(prefs, secure)
        translator = TranslatorService(this)
        emojiHistory = EmojiHistory.deserialize(prefs.emojiHistory)
        io.execute {
            userModel.load()
            clipboard.load()
            prefs.enabledLanguages.forEach { ensureDictionary(it) }
            Language.entries.forEach { ensureDictionary(it) }
            val paint = Paint()
            emojiCatalog = assets.open("emoji.tsv").use { input -> EmojiCatalog.load(input) { paint.hasGlyph(it) } }
        }
    }

    /** Loads a dictionary synchronously if needed (call from a background thread). */
    fun ensureDictionary(language: Language): Dictionary =
        dictionaries[language] ?: synchronized(dictionaries) {
            dictionaries[language] ?: assets.open("dict_${language.code}.txt").use { Dictionary.load(language, it) }.also {
                dictionaries[language] = it
                runCatching { bigrams[language] = assets.open("bigrams_${language.code}.txt").use(BigramModel::load) }
            }
        }

    fun dictionaryIfLoaded(language: Language): Dictionary? = dictionaries[language]

    fun saveEmojiHistory() {
        prefs.emojiHistory = emojiHistory.serialize()
    }

    /** Removes every piece of locally stored user data (settings stay unless [includeSettings]). */
    fun deleteAllData(includeSettings: Boolean) {
        userModel.clear()
        clipboard.clearAll()
        emojiHistory = EmojiHistory()
        prefs.emojiHistory = null
        secure.clearSecrets()
        translator.deleteAllModels()
        if (includeSettings) prefs.resetAll()
    }

    companion object {
        lateinit var instance: RobApp
            private set
    }
}
