package com.rob.aikeyboard.text

import java.util.Locale

/**
 * One-tap offline cleanup of a whole message: spacing around punctuation, typos, commas before
 * common conjunctions, sentence capitals and final punctuation. Rule-based, so it handles the
 * frequent cases only; the AI "Correct" action covers the rest.
 */
class QuickFix(private val engine: SuggestionEngine, private val detector: LanguageDetector) {

    fun fix(text: String, fallback: Language): String {
        if (text.isBlank()) return text
        val language = detector.detect(text, fallback) ?: fallback
        var t = spacing(text)
        t = spelling(t, language)
        t = commas(t, language)
        t = capitals(t)
        if (language == Language.EN) t = t.replace(Regex("(?<![\\p{L}'])i(?![\\p{L}])"), "I")
        t = finalPunctuation(t, language)
        return t
    }

    /** Single spaces, none before punctuation, one after it. */
    internal fun spacing(text: String): String = text
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex(" +([,.!?;:])"), "$1")
        .replace(Regex("([,;:])(?=\\p{L})"), "$1 ")
        .replace(Regex("([!?])(?=\\p{L})"), "$1 ")
        // After "." only before a capital, so abbreviations like "т.е." stay intact.
        .replace(Regex("(\\.)(?=\\p{Lu})"), "$1 ")
        .trim()

    private fun spelling(text: String, language: Language): String {
        val langs = when (language) {
            Language.RU -> listOf(Language.RU)
            Language.DE -> listOf(Language.DE, Language.EN)
            Language.EN -> listOf(Language.EN, Language.DE)
        }
        return WORD.replace(text) { m ->
            val w = m.value
            if (w.length < 2 || w.any(Char::isDigit) || engine.isValid(w, langs)) w
            else engine.suggest(w, langs).autoCorrection ?: w
        }
    }

    private fun commas(text: String, language: Language): String {
        val rules = COMMA_BEFORE[language] ?: return text
        var t = text
        for (word in rules) {
            // "… word" preceded by a letter (not sentence start, no comma yet) → "…, word"
            t = Regex("(?<=\\p{L})( )(?=${Regex.escape(word)}(?![\\p{L}-]))", RegexOption.IGNORE_CASE).replace(t) { m ->
                val before = t.substring(0, m.range.first)
                val prev = before.substringAfterLast(' ').lowercase(Locale.ROOT)
                if (prev in NO_COMMA_AFTER) m.value else ", "
            }
        }
        return t
    }

    /**
     * Comma to add while typing, right after a word is finished. Uses a narrower, safer word list
     * than [fix] because it acts without a button press.
     * @param before text up to and including the word that was just finished.
     * @return index in [before] of the space that should become ", ", or null.
     */
    fun liveComma(before: String, language: Language): Int? {
        val rules = LIVE_COMMA_BEFORE[language] ?: return null
        for (rule in rules) {
            val m = Regex("(?<=\\p{L}) (${Regex.escape(rule)})$", RegexOption.IGNORE_CASE).find(before) ?: continue
            val prefix = before.substring(0, m.range.first)
            val prev = prefix.substringAfterLast(' ').lowercase(Locale.ROOT)
            if (prev in NO_COMMA_AFTER || prev in LIVE_NO_COMMA_AFTER) return null
            return m.range.first
        }
        return null
    }

    internal fun capitals(text: String): String {
        val sb = StringBuilder(text)
        var capitalizeNext = true
        for (i in sb.indices) {
            val c = sb[i]
            when {
                c.isLetter() -> {
                    if (capitalizeNext) sb.setCharAt(i, c.uppercaseChar())
                    capitalizeNext = false
                }
                c in ".!?…" -> capitalizeNext = true
                c.isDigit() -> capitalizeNext = false
            }
        }
        return sb.toString()
    }

    private fun finalPunctuation(text: String, language: Language): String {
        val last = text.lastOrNull() ?: return text
        if (!last.isLetterOrDigit()) return text
        val lastSentence = text.split(Regex("[.!?…]")).last().trim().lowercase(Locale.ROOT)
        val clauses = lastSentence.split(',').map { c -> Regex("[\\p{L}'-]+").findAll(c).map { it.value }.toList() }
        // Only after a one-word opener ("Привет, как…"); "Я думаю, что…" is a statement.
        val opener = clauses.size > 1 && clauses.first().size == 1
        val question = clauses.first().firstOrNull() in QUESTION_WORDS[language].orEmpty() ||
            (opener && clauses[1].firstOrNull() in QUESTION_WORDS[language].orEmpty()) ||
            (language == Language.RU && clauses.any { "ли" in it })
        return text + if (question) "?" else "."
    }

    companion object {
        private val WORD = Regex("[\\p{L}']+")

        private val COMMA_BEFORE = mapOf(
            Language.RU to listOf(
                "потому что", "так как", "а", "но", "что", "чтобы", "который", "которая", "которое", "которые",
                "которого", "которой", "которому", "которым", "которых", "если", "хотя", "поэтому", "зато", "однако",
            ),
            Language.DE to listOf("dass", "weil", "aber", "obwohl", "sondern", "damit", "ob", "wenn", "als ob"),
            Language.EN to listOf("but"),
        )

        /** Multi-word rules first, so "потому что" puts the comma before "потому". */
        private val LIVE_COMMA_BEFORE = mapOf(
            Language.RU to listOf(
                "потому что", "так как", "так что", "но", "чтобы", "что", "который", "которая", "которое", "которые",
                "которого", "которой", "которому", "которым", "которых", "хотя", "зато", "однако",
            ),
            Language.DE to listOf("dass", "weil", "aber", "obwohl", "sondern"),
            Language.EN to listOf("but"),
        )

        /** "ты что", "а что", "вот что" are not subordinate clauses. */
        private val LIVE_NO_COMMA_AFTER = setOf(
            "а", "но", "вот", "ну", "это", "чем", "я", "ты", "вы", "он", "она", "оно", "мы", "они", "ещё", "еще", "уж", "ведь", "же",
        )

        /** Words after which no comma goes before the conjunction ("потому что", "не то что"). */
        private val NO_COMMA_AFTER = setOf("потому", "так", "то", "не", "и", "или", "да", "und", "oder", "and", "or", "als")

        private val QUESTION_WORDS = mapOf(
            Language.RU to setOf("как", "где", "что", "почему", "зачем", "когда", "кто", "сколько", "куда", "откуда", "какой", "какая", "какие", "чей", "можно"),
            Language.DE to setOf("wie", "was", "wo", "warum", "wieso", "weshalb", "wer", "wann", "wohin", "woher", "welche", "welcher", "kannst", "hast", "bist"),
            Language.EN to setOf("how", "what", "where", "why", "who", "when", "which", "can", "do", "does", "did", "are", "is", "will"),
        )
    }
}
