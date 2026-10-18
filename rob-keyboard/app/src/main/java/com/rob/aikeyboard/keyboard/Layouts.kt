package com.rob.aikeyboard.keyboard

import com.rob.aikeyboard.text.Language

enum class KeyType { CHAR, SHIFT, DELETE, SYMBOLS, SYMBOLS_MORE, ALPHA, ENTER, SPACE, EMOJI, COMMA, PERIOD }

data class Key(
    val type: KeyType,
    /** Text the key inserts (lowercase form for letters). */
    val text: String = "",
    val width: Float = 1f,
    /** Small hint drawn in the top-right corner (usually the long-press digit). */
    val hint: String? = null,
    /** Characters offered on long press. */
    val alternates: List<String> = emptyList(),
) {
    val isModifier: Boolean get() = type != KeyType.CHAR && type != KeyType.SPACE && type != KeyType.COMMA && type != KeyType.PERIOD
}

data class KeyboardLayout(val id: String, val rows: List<List<Key>>) {
    val maxRowUnits: Float = rows.maxOf { row -> row.sumOf { it.width.toDouble() }.toFloat() }
}

object Layouts {
    private val digits = "1234567890"

    private val latinAlternates = mapOf(
        "a" to "àáâäæãåā", "e" to "èéêëēę", "i" to "ìíîïī", "o" to "òóôöõøœō", "u" to "ùúûüū",
        "c" to "çćč", "n" to "ñń", "s" to "ßśš", "y" to "ýÿ", "z" to "žźż", "l" to "ł", "g" to "ğ",
    )
    private val cyrillicAlternates = mapOf("е" to "ё", "ь" to "ъ", "и" to "й", "г" to "ґ", "і" to "ї")

    private fun letterRow(letters: String, withDigits: Boolean, alternates: Map<String, String>): List<Key> =
        letters.mapIndexed { i, c ->
            val s = c.toString()
            val digit = if (withDigits && i < digits.length) digits[i].toString() else null
            val alts = listOfNotNull(digit) + (alternates[s]?.map(Char::toString) ?: emptyList())
            Key(KeyType.CHAR, s, hint = digit, alternates = alts)
        }

    private val punctuation = listOf(",", "?", "!", "'", "\"", ":", ";", "-", "(", ")", "/", "@", "…")

    fun bottomRow(units: Float, alpha: Boolean): List<Key> {
        val side = 1.5f
        val small = 1.15f
        val space = units - side * 2 - small * 3
        return listOf(
            Key(if (alpha) KeyType.SYMBOLS else KeyType.ALPHA, if (alpha) "123" else "ABC", side),
            Key(KeyType.EMOJI, width = small),
            // Long press on comma starts voice input (as on the reference design).
            Key(KeyType.COMMA, ",", small),
            Key(KeyType.SPACE, " ", space),
            Key(KeyType.PERIOD, ".", small, hint = "!?", alternates = punctuation),
            Key(KeyType.ENTER, "\n", side),
        )
    }

    fun forLanguage(language: Language): KeyboardLayout = when (language) {
        Language.RU -> {
            val a = cyrillicAlternates
            KeyboardLayout(
                "ru",
                listOf(
                    letterRow("йцукенгшщзхъ", true, a),
                    letterRow("фывапролджэ", false, a),
                    listOf(Key(KeyType.SHIFT, width = 1.5f)) + letterRow("ячсмитьбю", false, a) + Key(KeyType.DELETE, width = 1.5f),
                    bottomRow(12f, true),
                ),
            )
        }
        Language.DE -> {
            val a = latinAlternates
            KeyboardLayout(
                "de",
                listOf(
                    letterRow("qwertzuiopü", true, a),
                    letterRow("asdfghjklöä", false, a),
                    listOf(Key(KeyType.SHIFT, width = 1.5f)) + letterRow("yxcvbnm", false, a) + Key(KeyType.DELETE, width = 1.5f),
                    bottomRow(11f, true),
                ),
            )
        }
        Language.EN -> {
            val a = latinAlternates
            KeyboardLayout(
                "en",
                listOf(
                    letterRow("qwertyuiop", true, a),
                    letterRow("asdfghjkl", false, a),
                    listOf(Key(KeyType.SHIFT, width = 1.5f)) + letterRow("zxcvbnm", false, a) + Key(KeyType.DELETE, width = 1.5f),
                    bottomRow(10f, true),
                ),
            )
        }
    }

    private fun chars(s: String) = s.map { Key(KeyType.CHAR, it.toString()) }

    val symbols = KeyboardLayout(
        "symbols",
        listOf(
            chars("1234567890"),
            chars("@#€%&-+()/"),
            listOf(Key(KeyType.SYMBOLS_MORE, "=\\<", 1.5f)) + chars("*\"':;!?") + Key(KeyType.DELETE, width = 1.5f),
            bottomRow(10f, false),
        ),
    )

    val symbolsMore = KeyboardLayout(
        "symbols_more",
        listOf(
            chars("~`|•√π÷×¶∆"),
            chars("£¥$¢^°={}\\"),
            listOf(Key(KeyType.SYMBOLS, "?123", 1.5f)) + chars("_©®™✓[]<>").take(7) + Key(KeyType.DELETE, width = 1.5f),
            bottomRow(10f, false),
        ),
    )

    val numberPad = KeyboardLayout(
        "number",
        listOf(
            chars("123") + Key(KeyType.CHAR, "-", alternates = listOf("+", "/")),
            chars("456") + Key(KeyType.CHAR, ","),
            chars("789") + Key(KeyType.DELETE),
            listOf(Key(KeyType.SYMBOLS, "#+=")) + chars("0.") + Key(KeyType.ENTER, "\n"),
        ),
    )

    val phonePad = KeyboardLayout(
        "phone",
        listOf(
            chars("123") + Key(KeyType.CHAR, "-"),
            chars("456") + Key(KeyType.CHAR, " "),
            chars("789") + Key(KeyType.DELETE),
            listOf(Key(KeyType.CHAR, "*", alternates = listOf("#", "(", ")", "/", "N", ";", ","))) +
                Key(KeyType.CHAR, "0", hint = "+", alternates = listOf("+")) + Key(KeyType.CHAR, "#") + Key(KeyType.ENTER, "\n"),
        ),
    )
}
