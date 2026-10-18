package com.rob.aikeyboard.spell

import android.service.textservice.SpellCheckerService
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import com.rob.aikeyboard.RobApp
import com.rob.aikeyboard.text.Language

/**
 * System spell checker backed by the keyboard's offline dictionaries. Once selected in
 * Android settings, apps underline unknown words and offer corrections.
 */
class RobSpellCheckerService : SpellCheckerService() {
    override fun createSession(): Session = RobSession()

    private class RobSession : Session() {
        private lateinit var languages: List<Language>

        override fun onCreate() {
            val code = locale?.take(2)
            val primary = Language.fromCode(code)
            languages = if (primary != null) listOf(primary) else listOf(Language.RU, Language.DE, Language.EN)
            languages.forEach { RobApp.instance.ensureDictionary(it) }
        }

        override fun onGetSuggestions(textInfo: TextInfo, suggestionsLimit: Int): SuggestionsInfo {
            val word = textInfo.text.orEmpty().trim()
            val engine = RobApp.instance.engine
            if (word.isEmpty() || word.none(Char::isLetter) || engine.isValid(word, languages)) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }
            val result = engine.suggest(word, languages)
            val suggestions = (listOfNotNull(result.autoCorrection) + result.suggestions)
                .filter { it != word }.distinct().take(suggestionsLimit.coerceAtLeast(1))
            return SuggestionsInfo(
                SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or
                    (if (suggestions.isNotEmpty()) SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS else 0),
                suggestions.toTypedArray(),
            )
        }
    }
}
