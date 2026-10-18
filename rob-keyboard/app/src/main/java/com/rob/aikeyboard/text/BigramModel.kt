package com.rob.aikeyboard.text

import java.io.InputStream
import java.util.Locale

/** Bundled next-word table (`assets/bigrams_<lang>.txt`: `word<TAB>next1|next2|…`). */
class BigramModel(private val table: Map<String, List<String>>) {

    fun next(previous: String): List<String> = table[previous.lowercase(Locale.ROOT)].orEmpty()

    companion object {
        fun load(input: InputStream): BigramModel {
            val map = HashMap<String, List<String>>(8000)
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) map[line.substring(0, tab)] = line.substring(tab + 1).split('|')
                }
            }
            return BigramModel(map)
        }
    }
}
