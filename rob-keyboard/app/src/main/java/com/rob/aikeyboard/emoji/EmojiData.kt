package com.rob.aikeyboard.emoji

import java.io.InputStream
import java.util.Locale

data class EmojiItem(
    val emoji: String,
    val group: String,
    val keywords: List<String>,
    val skinTones: List<String>,
)

enum class EmojiGroup(val id: String, val icon: String, val label: String) {
    RECENT("recent", "🕘", "Недавние"),
    FAVORITES("favorites", "⭐", "Избранные"),
    SMILEYS("smileys", "😀", "Смайлы"),
    PEOPLE("people", "👋", "Люди"),
    NATURE("nature", "🐻", "Природа"),
    FOOD("food", "🍔", "Еда"),
    TRAVEL("travel", "🚗", "Путешествия"),
    ACTIVITIES("activities", "⚽", "Активности"),
    OBJECTS("objects", "💡", "Предметы"),
    SYMBOLS("symbols", "❤️", "Символы"),
    FLAGS("flags", "🏳️", "Флаги"),
}

/** Unicode emoji catalogue from `assets/emoji.tsv` (built from emoji-test.txt + CLDR keywords). */
class EmojiCatalog(val items: List<EmojiItem>) {
    private val byGroup = items.groupBy { it.group }

    fun group(group: EmojiGroup): List<EmojiItem> = byGroup[group.id].orEmpty()

    fun find(emoji: String): EmojiItem? = items.firstOrNull { it.emoji == emoji }

    /** Keyword search across English, German and Russian CLDR annotations. */
    fun search(query: String, limit: Int = 120): List<EmojiItem> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()
        return items.asSequence()
            .mapNotNull { item ->
                val rank = item.keywords.minOfOrNull { kw ->
                    when {
                        kw == q -> 0
                        kw.startsWith(q) -> 1
                        kw.split(' ').any { it.startsWith(q) } -> 2
                        else -> Int.MAX_VALUE
                    }
                } ?: Int.MAX_VALUE
                if (rank == Int.MAX_VALUE) null else rank to item
            }
            .sortedBy { it.first }
            .map { it.second }
            .take(limit)
            .toList()
    }

    companion object {
        /**
         * @param supported filters out emoji the device font cannot draw (e.g. newer Unicode
         *   versions on older Android builds).
         */
        fun load(input: InputStream, supported: (String) -> Boolean = { true }): EmojiCatalog {
            val bases = LinkedHashMap<String, EmojiItem>()
            val tones = HashMap<String, MutableList<String>>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    val cols = line.split('\t')
                    if (cols.size < 5) return@forEach
                    val (emoji, group, _, keywords, base) = cols
                    if (!supported(emoji)) return@forEach
                    if (base.isNotEmpty()) {
                        tones.getOrPut(base) { mutableListOf() } += emoji
                    } else {
                        bases[emoji] = EmojiItem(emoji, group, keywords.split('|').filter(String::isNotEmpty), emptyList())
                    }
                }
            }
            return EmojiCatalog(bases.values.map { it.copy(skinTones = tones[it.emoji].orEmpty()) })
        }
    }
}

/** Recently used, frequently used and favourite emoji; serialised into a single preference string. */
class EmojiHistory(private val maxRecent: Int = 40) {
    private val recent = ArrayDeque<String>()
    private val counts = LinkedHashMap<String, Int>()
    private val favorites = LinkedHashSet<String>()

    fun use(emoji: String) {
        recent.remove(emoji)
        recent.addFirst(emoji)
        while (recent.size > maxRecent) recent.removeLast()
        counts.merge(emoji, 1, Int::plus)
    }

    fun recent(): List<String> = recent.toList()

    fun frequent(limit: Int = 24): List<String> =
        counts.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.map { it.key }.take(limit)

    fun favorites(): List<String> = favorites.toList()

    fun isFavorite(emoji: String) = emoji in favorites

    fun toggleFavorite(emoji: String) {
        if (!favorites.remove(emoji)) favorites += emoji
    }

    fun clear() {
        recent.clear()
        counts.clear()
    }

    fun serialize(): String = listOf(
        recent.joinToString(SEP),
        counts.entries.joinToString(SEP) { "${it.key}=${it.value}" },
        favorites.joinToString(SEP),
    ).joinToString(SECTION)

    companion object {
        private const val SEP = "\u001F"
        private const val SECTION = "\u001E"

        fun deserialize(data: String?): EmojiHistory {
            val h = EmojiHistory()
            if (data.isNullOrEmpty()) return h
            val parts = data.split(SECTION)
            parts.getOrNull(0)?.split(SEP)?.filter(String::isNotEmpty)?.forEach { h.recent.addLast(it) }
            parts.getOrNull(1)?.split(SEP)?.forEach { e ->
                val i = e.lastIndexOf('=')
                if (i > 0) h.counts[e.substring(0, i)] = e.substring(i + 1).toIntOrNull() ?: 1
            }
            parts.getOrNull(2)?.split(SEP)?.filter(String::isNotEmpty)?.forEach { h.favorites += it }
            return h
        }
    }
}
