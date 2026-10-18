package com.rob.aikeyboard.text

import java.io.BufferedReader
import java.io.InputStream
import java.util.Locale
import kotlin.math.ln

/**
 * Read-only word list for one language, loaded from `assets/dict_<lang>.txt`
 * (lines of `word<TAB>frequency`, most frequent first).
 *
 * Words are stored sorted by their lowercase key so that prefix lookups are a binary search.
 */
class Dictionary private constructor(
    val language: Language,
    private val keys: Array<String>,
    private val words: Array<String>,
    private val freqs: IntArray,
) {
    private val keyIndex: Map<String, Int> = HashMap<String, Int>(keys.size * 2).apply {
        this@Dictionary.keys.forEachIndexed { i, k -> putIfAbsent(k, i) }
    }
    private val maxLogFreq = freqs.maxOrNull()?.let { ln(it.toDouble() + 1) } ?: 1.0

    val size: Int get() = keys.size

    fun contains(word: String): Boolean = keyIndex.containsKey(normalize(word))

    /** Dictionary spelling of [word] (e.g. German noun capitalisation), or null. */
    fun canonical(word: String): String? = keyIndex[normalize(word)]?.let { words[it] }

    fun frequency(word: String): Int = keyIndex[normalize(word)]?.let { freqs[it] } ?: 0

    /** 0..1, log-scaled frequency relative to the most frequent word. */
    fun weight(index: Int): Double = ln(freqs[index].toDouble() + 1) / maxLogFreq

    fun weight(word: String): Double = keyIndex[normalize(word)]?.let { weight(it) } ?: 0.0

    /** Words that start with [prefix], most frequent first. */
    fun completions(prefix: String, limit: Int): List<Candidate> {
        val p = normalize(prefix)
        if (p.isEmpty()) return emptyList()
        val out = ArrayList<Candidate>()
        var i = lowerBound(p)
        while (i < keys.size && keys[i].startsWith(p)) {
            out += Candidate(words[i], weight(i), 0.0, language)
            i++
        }
        out.sortByDescending { it.weight }
        return out.take(limit)
    }

    /** Words within [maxDistance] edits of [typed], scored by distance and frequency. */
    fun corrections(typed: String, maxDistance: Double, limit: Int): List<Candidate> {
        val t = normalize(typed)
        if (t.length < 2) return emptyList()
        // A wrong first letter is rare; only scan words starting with it, a neighbour or a diacritic twin.
        // Keys are sorted, so each first letter is one contiguous range.
        val firsts = Diacritics.variants(t[0]) + KeyProximity.neighboursOf(t[0])
        val out = ArrayList<Candidate>()
        for (f in firsts) {
            var i = lowerBound(f.toString())
            while (i < keys.size && keys[i][0] == f) {
                val k = keys[i]
                if (kotlin.math.abs(k.length - t.length) <= 2) {
                    val d = EditDistance.weighted(t, k, maxDistance)
                    if (d <= maxDistance) out += Candidate(words[i], weight(i), d, language)
                }
                i++
            }
        }
        out.sortByDescending { it.score }
        return out.take(limit)
    }

    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        fun normalize(word: String): String = word.lowercase(Locale.ROOT)

        fun load(language: Language, input: InputStream): Dictionary {
            val entries = ArrayList<Triple<String, String, Int>>(45_000)
            input.bufferedReader(Charsets.UTF_8).use { r: BufferedReader ->
                r.forEachLine { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) {
                        val w = line.substring(0, tab)
                        val f = line.substring(tab + 1).trim().toIntOrNull() ?: 1
                        entries += Triple(normalize(w), w, f)
                    }
                }
            }
            return fromEntries(language, entries)
        }

        fun fromWords(language: Language, words: Map<String, Int>): Dictionary =
            fromEntries(language, words.map { (w, f) -> Triple(normalize(w), w, f) })

        private fun fromEntries(language: Language, entries: List<Triple<String, String, Int>>): Dictionary {
            val sorted = entries.sortedWith(compareBy<Triple<String, String, Int>> { it.first }.thenByDescending { it.third })
            return Dictionary(
                language,
                Array(sorted.size) { sorted[it].first },
                Array(sorted.size) { sorted[it].second },
                IntArray(sorted.size) { sorted[it].third },
            )
        }
    }
}

data class Candidate(
    val word: String,
    /** 0..1 frequency weight. */
    val weight: Double,
    /** Weighted edit distance from the typed word; 0 for exact/prefix matches. */
    val distance: Double,
    val language: Language,
) {
    val score: Double get() = weight - distance * 0.45
}

enum class Language(val code: String, val displayName: String, val bcp47: String) {
    RU("ru", "Русский", "ru-RU"),
    DE("de", "Deutsch", "de-DE"),
    EN("en", "English", "en-US");

    companion object {
        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }
    }
}
