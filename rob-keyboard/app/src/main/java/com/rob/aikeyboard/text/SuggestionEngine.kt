package com.rob.aikeyboard.text

import java.util.Locale

data class SuggestResult(
    val typed: String,
    /** Up to three words for the suggestion strip; the middle slot is the best guess. */
    val suggestions: List<String>,
    /** Word to substitute when the user finishes [typed] with a separator, or null. */
    val autoCorrection: String?,
    val typedIsValid: Boolean,
)

/**
 * Offline completion, spelling correction and next-word prediction.
 * Combines the bundled frequency dictionaries with what the user has typed before ([UserModel]).
 */
class SuggestionEngine(
    private val dictionary: (Language) -> Dictionary?,
    private val user: UserModel,
    private val bigrams: (Language) -> BigramModel? = { null },
) {

    fun isValid(word: String, languages: List<Language>): Boolean =
        user.isKnown(word) || languages.any { dictionary(it)?.contains(word) == true } || word.any(Char::isDigit)

    fun suggest(typed: String, languages: List<Language>, autoCorrectEnabled: Boolean = true): SuggestResult {
        if (typed.isEmpty()) return SuggestResult(typed, emptyList(), null, true)
        val primary = languages.firstOrNull()
        val valid = isValid(typed, languages)

        val special = if (primary == Language.EN || Language.EN in languages) EN_FIXES[typed.lowercase(Locale.ROOT)] else null

        val pool = LinkedHashMap<String, Double>()
        fun offer(word: String, score: Double) {
            val key = word.lowercase(Locale.ROOT)
            val existing = pool.entries.firstOrNull { it.key.lowercase(Locale.ROOT) == key }
            if (existing == null) pool[word] = score else if (score > existing.value) existing.setValue(score)
        }

        languages.forEachIndexed { idx, lang ->
            val dict = dictionary(lang) ?: return@forEachIndexed
            val langBias = if (idx == 0) 0.0 else 0.08
            dict.completions(typed, 6).forEach { c ->
                // Prefer completions that are not much longer than what was typed.
                val lengthPenalty = 0.02 * (c.word.length - typed.length)
                offer(c.word, c.weight - lengthPenalty - langBias + if (c.word.length == typed.length) 0.25 else 0.0)
            }
            if (!valid || typed.length >= 3) {
                dict.corrections(typed, maxDistanceFor(typed), 6).forEach { c ->
                    offer(c.word, c.score - langBias)
                }
            }
        }
        user.completions(typed, 3).forEach { offer(it, 0.9) }
        special?.let { offer(it, 2.0) }

        val ranked = pool.entries.sortedByDescending { it.value }.map { matchCase(it.key, typed) }
            .filter { it != typed }

        val autoCorrection = when {
            !autoCorrectEnabled -> null
            special != null -> special
            valid -> rareMisspelling(typed, languages)
            else -> bestCorrection(typed, languages)
        }

        // Strip layout: [typed or alternative] [best] [alternative]
        val best = autoCorrection ?: ranked.firstOrNull()
        val others = ranked.filter { it != best }
        val strip = buildList {
            add(if (autoCorrection != null) typed else others.getOrNull(0) ?: typed)
            best?.let(::add)
            others.firstOrNull { it != this.firstOrNull() }?.let(::add)
        }.distinct().filter { it.isNotEmpty() }

        return SuggestResult(typed, strip, autoCorrection, valid)
    }

    /** Best spelling correction that is confident enough to apply automatically. */
    private fun bestCorrection(typed: String, languages: List<Language>): String? {
        if (typed.length < 2 || typed.any { !it.isLetter() && it != '\'' && it != '-' }) return null
        val maxDistance = maxDistanceFor(typed)
        val candidates = languages.mapIndexedNotNull { idx, lang ->
            dictionary(lang)?.corrections(typed, maxDistance, 3)?.firstOrNull()?.let { it to idx }
        }
        val (best, _) = candidates.maxByOrNull { (c, idx) -> c.score - idx * 0.08 } ?: return null
        if (best.weight < MIN_AUTOCORRECT_WEIGHT) return null
        return matchCase(best.word, typed)
    }

    /**
     * Subtitle-derived word lists contain a few common misspellings ("vieleicht"). A rare valid
     * word that is one doubled letter or diacritic away from a far more frequent word is
     * treated as a typo of it.
     */
    private fun rareMisspelling(typed: String, languages: List<Language>): String? {
        if (user.isKnown(typed) || typed.length < 4) return null
        val lang = languages.firstOrNull() ?: return null
        val dict = dictionary(lang) ?: return null
        val typedWeight = dict.weight(typed)
        if (typedWeight == 0.0 || typedWeight > 0.45) return null
        val best = dict.corrections(typed, 0.5, 3)
            .firstOrNull { it.distance > 0.0 && it.weight > typedWeight + 0.25 } ?: return null
        return matchCase(best.word, typed)
    }

    fun predictNext(previousWord: String?, languages: List<Language>, limit: Int = 3): List<String> {
        if (previousWord.isNullOrEmpty()) {
            val starters = user.sentenceStarters(limit).ifEmpty { STARTERS[languages.firstOrNull()] ?: emptyList() }
            return starters.take(limit)
        }
        val learned = user.nextWords(previousWord, limit)
        val base = languages.flatMap { bigrams(it)?.next(previousWord).orEmpty() }
        return (learned + base).distinctBy { it.lowercase(Locale.ROOT) }.take(limit)
    }

    private fun maxDistanceFor(word: String): Double = when {
        word.length <= 3 -> 0.75
        word.length <= 5 -> 1.0
        word.length <= 8 -> 1.6
        else -> 2.0
    }

    companion object {
        private const val MIN_AUTOCORRECT_WEIGHT = 0.30

        /** Apply the casing the user typed (capitalised, all caps) to a candidate. */
        fun matchCase(candidate: String, typed: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> candidate.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> candidate.replaceFirstChar { it.uppercaseChar() }
            else -> candidate
        }

        private val EN_FIXES = mapOf(
            "i" to "I", "im" to "I'm", "ive" to "I've", "ill" to "I'll", "id" to "I'd",
            "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't", "cant" to "can't",
            "wont" to "won't", "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't",
            "werent" to "weren't", "couldnt" to "couldn't", "shouldnt" to "shouldn't",
            "wouldnt" to "wouldn't", "havent" to "haven't", "hasnt" to "hasn't", "thats" to "that's",
            "whats" to "what's", "youre" to "you're", "theyre" to "they're", "lets" to "let's",
        )

        private val STARTERS = mapOf(
            Language.RU to listOf("Я", "Привет", "Да"),
            Language.DE to listOf("Ich", "Hallo", "Ja"),
            Language.EN to listOf("I", "Hi", "The"),
        )
    }
}
