package com.rob.aikeyboard.text

import kotlin.math.min

/** Characters that are the same letter with or without a diacritic. */
object Diacritics {
    private val pairs = mapOf(
        'ё' to 'е', 'й' to 'и', 'ъ' to 'ь',
        'ä' to 'a', 'ö' to 'o', 'ü' to 'u', 'ß' to 's',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'à' to 'a', 'á' to 'a', 'ç' to 'c', 'ñ' to 'n',
    )

    fun base(c: Char): Char = pairs[c] ?: c

    fun equivalent(a: Char, b: Char): Boolean = a != b && base(a) == base(b)
}

/** Physical key adjacency on the three supported layouts, for typo-aware distances. */
object KeyProximity {
    private val rows = listOf(
        listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю"),
        listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm"),
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
    )
    private val neighbours: Map<Char, Set<Char>> = buildMap<Char, MutableSet<Char>> {
        for (layout in rows) {
            for ((r, row) in layout.withIndex()) {
                for ((c, ch) in row.withIndex()) {
                    val set = getOrPut(ch) { mutableSetOf() }
                    row.getOrNull(c - 1)?.let(set::add)
                    row.getOrNull(c + 1)?.let(set::add)
                    // Rows are staggered by roughly half a key.
                    layout.getOrNull(r - 1)?.let { up -> up.getOrNull(c)?.let(set::add); up.getOrNull(c + 1)?.let(set::add) }
                    layout.getOrNull(r + 1)?.let { dn -> dn.getOrNull(c)?.let(set::add); dn.getOrNull(c - 1)?.let(set::add) }
                }
            }
        }
    }

    fun areNeighbours(a: Char, b: Char): Boolean = neighbours[a]?.contains(b) == true
}

object EditDistance {
    private const val NEIGHBOUR_SUB = 0.6
    private const val DIACRITIC_SUB = 0.15
    private const val TRANSPOSE = 0.7
    private const val INDEL = 1.0
    private const val DOUBLED = 0.5

    private fun subCost(a: Char, b: Char): Double = when {
        a == b -> 0.0
        Diacritics.equivalent(a, b) -> DIACRITIC_SUB
        KeyProximity.areNeighbours(a, b) -> NEIGHBOUR_SUB
        else -> 1.0
    }

    /**
     * Damerau–Levenshtein distance with cheaper neighbour/diacritic substitutions and
     * doubled-letter insertions/deletions. Returns a value > [limit] as soon as the
     * distance is known to exceed it.
     */
    fun weighted(s: String, t: String, limit: Double = Double.MAX_VALUE): Double {
        val n = s.length
        val m = t.length
        var prev2 = DoubleArray(m + 1)
        var prev = DoubleArray(m + 1) { it * INDEL }
        var cur = DoubleArray(m + 1)
        var prevRowMin = 0.0
        for (i in 1..n) {
            cur[0] = i * INDEL
            var rowMin = cur[0]
            for (j in 1..m) {
                val sc = s[i - 1]
                val tc = t[j - 1]
                val del = if (i >= 2 && s[i - 2] == sc) DOUBLED else INDEL
                val ins = if (j >= 2 && t[j - 2] == tc) DOUBLED else INDEL
                var v = min(min(prev[j] + del, cur[j - 1] + ins), prev[j - 1] + subCost(sc, tc))
                if (i > 1 && j > 1 && sc == t[j - 2] && s[i - 2] == tc) {
                    v = min(v, prev2[j - 2] + TRANSPOSE)
                }
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            // A transposition can still reach back two rows, so both must exceed the limit.
            if (rowMin > limit && prevRowMin > limit) return limit + 1
            prevRowMin = rowMin
            val tmp = prev2
            prev2 = prev
            prev = cur
            cur = tmp
        }
        return prev[m]
    }
}
