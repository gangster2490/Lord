package com.rob.aikeyboard.text

import org.json.JSONObject
import java.util.Locale

/**
 * What the keyboard has learned from the user: words missing from the bundled dictionaries
 * and which word tends to follow which. Never fed from password or "no learning" fields.
 * Persisted encrypted by [com.rob.aikeyboard.data.UserModelStore].
 */
class UserModel {
    private val words = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private val starters = HashMap<String, Int>()

    @Volatile
    var dirty = false
        private set

    @Synchronized
    fun learn(previous: String?, word: String) {
        if (word.isBlank() || word.length > 40 || word.any(Char::isDigit)) return
        words.merge(word, 1, Int::plus)
        if (previous == null) {
            starters.merge(word, 1, Int::plus)
        } else {
            bigrams.getOrPut(key(previous)) { HashMap() }.merge(word, 1, Int::plus)
        }
        dirty = true
        trim()
    }

    @Synchronized
    fun addWord(word: String) {
        words[word] = maxOf(words[word] ?: 0, KNOWN_THRESHOLD)
        dirty = true
    }

    @Synchronized
    fun forget(word: String) {
        words.remove(word)
        dirty = true
    }

    /** A word counts as known once typed (and kept) a few times, or added explicitly. */
    @Synchronized
    fun isKnown(word: String): Boolean = (words[word] ?: 0) >= KNOWN_THRESHOLD

    @Synchronized
    fun completions(prefix: String, limit: Int): List<String> {
        val p = prefix.lowercase(Locale.ROOT)
        return words.entries.asSequence()
            .filter { it.value >= KNOWN_THRESHOLD && it.key.lowercase(Locale.ROOT).startsWith(p) && it.key.length > prefix.length }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
            .toList()
    }

    @Synchronized
    fun nextWords(previous: String, limit: Int): List<String> =
        bigrams[key(previous)]?.entries?.sortedByDescending { it.value }?.map { it.key }?.take(limit) ?: emptyList()

    @Synchronized
    fun sentenceStarters(limit: Int): List<String> =
        starters.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.map { it.key }.take(limit)

    @Synchronized
    fun clear() {
        words.clear()
        bigrams.clear()
        starters.clear()
        dirty = true
    }

    @Synchronized
    fun toJson(): String {
        dirty = false
        return JSONObject().apply {
            put("words", JSONObject(words as Map<*, *>))
            put("starters", JSONObject(starters as Map<*, *>))
            put("bigrams", JSONObject().apply { bigrams.forEach { (k, v) -> put(k, JSONObject(v as Map<*, *>)) } })
        }.toString()
    }

    @Synchronized
    fun loadJson(json: String) {
        clear()
        val o = JSONObject(json)
        o.optJSONObject("words")?.let { w -> w.keys().forEach { words[it] = w.getInt(it) } }
        o.optJSONObject("starters")?.let { w -> w.keys().forEach { starters[it] = w.getInt(it) } }
        o.optJSONObject("bigrams")?.let { b ->
            b.keys().forEach { k ->
                val inner = b.getJSONObject(k)
                bigrams[k] = HashMap<String, Int>().apply { inner.keys().forEach { put(it, inner.getInt(it)) } }
            }
        }
        dirty = false
    }

    private fun key(word: String) = word.lowercase(Locale.ROOT)

    private fun trim() {
        if (words.size > MAX_WORDS) {
            words.entries.sortedBy { it.value }.take(words.size - MAX_WORDS).forEach { words.remove(it.key) }
        }
        if (bigrams.size > MAX_WORDS) {
            bigrams.entries.sortedBy { e -> e.value.values.sum() }.take(bigrams.size - MAX_WORDS).forEach { bigrams.remove(it.key) }
        }
    }

    companion object {
        const val KNOWN_THRESHOLD = 2
        private const val MAX_WORDS = 5000
    }
}
