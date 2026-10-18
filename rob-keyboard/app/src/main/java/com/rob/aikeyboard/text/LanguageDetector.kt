package com.rob.aikeyboard.text

/**
 * Lightweight offline language guess for Russian / German / English text:
 * script first (Cyrillic), then German-only letters, then dictionary hits.
 */
class LanguageDetector(private val dictionary: (Language) -> Dictionary?) {

    fun detect(text: String, fallback: Language? = null): Language? {
        val letters = text.filter(Char::isLetter)
        if (letters.isEmpty()) return fallback
        val cyrillic = letters.count { it in 'Ѐ'..'ӿ' }
        if (cyrillic * 2 >= letters.length) return Language.RU

        val words = WORD.findAll(text).map { it.value }.filter { it.length > 1 }.toList().takeLast(12)
        if (words.isEmpty()) return fallback
        var de = 0.0
        var en = 0.0
        if (text.any { it in "äöüßÄÖÜ" }) de += 1.5
        val deDict = dictionary(Language.DE)
        val enDict = dictionary(Language.EN)
        for (w in words) {
            de += deDict?.weight(w) ?: 0.0
            en += enDict?.weight(w) ?: 0.0
        }
        return when {
            de == 0.0 && en == 0.0 -> fallback
            de > en * 1.15 -> Language.DE
            en > de * 1.15 -> Language.EN
            else -> fallback ?: if (de >= en) Language.DE else Language.EN
        }
    }

    companion object {
        private val WORD = Regex("[\\p{L}']+")
    }
}
