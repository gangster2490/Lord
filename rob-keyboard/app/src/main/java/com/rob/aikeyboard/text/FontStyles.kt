package com.rob.aikeyboard.text

/**
 * Decorative Unicode "fonts" (𝓗𝓮𝓵𝓵𝓸, 𝐇𝐞𝐥𝐥𝐨…) for TikTok names, bios and comments.
 * They are ordinary characters, so they paste anywhere. Latin letters and digits only; the line
 * styles also work on Cyrillic. German umlauts get a combining diaeresis; ß stays as is.
 */
object FontStyles {
    class Style(val id: String, val label: String, private val map: (Char) -> String?) {
        fun apply(c: Char): String = map(c) ?: c.toString()
        fun apply(text: String): String = buildString { text.forEach { append(apply(it)) } }
        val sample: String get() = apply("Hello 123")
    }

    private val UMLAUTS = mapOf('ä' to 'a', 'ö' to 'o', 'ü' to 'u', 'Ä' to 'A', 'Ö' to 'O', 'Ü' to 'U')
    private const val DIAERESIS = "̈"

    private fun cp(code: Int) = String(Character.toChars(code))

    /** Wraps [base] so umlauts resolve through the same alphabet. */
    private fun withUmlauts(base: (Char) -> String?): (Char) -> String? = { c ->
        UMLAUTS[c]?.let { base(it)?.plus(DIAERESIS) } ?: base(c)
    }

    private fun plain(upper: Int, lower: Int, digit: Int?, exceptions: Map<Char, String> = emptyMap()) =
        withUmlauts { c ->
            exceptions[c] ?: when (c) {
                in 'A'..'Z' -> cp(upper + (c - 'A'))
                in 'a'..'z' -> cp(lower + (c - 'a'))
                in '0'..'9' -> digit?.let { cp(it + (c - '0')) }
                else -> null
            }
        }

    private fun combining(mark: String): (Char) -> String? = { c -> if (c.isLetterOrDigit()) c + mark else null }

    val all: List<Style> = listOf(
        Style("bold", "Жирный", plain(0x1D400, 0x1D41A, 0x1D7CE)),
        Style("italic", "Курсив", plain(0x1D434, 0x1D44E, null, mapOf('h' to "ℎ"))),
        Style("bold_italic", "Жирный курсив", plain(0x1D468, 0x1D482, 0x1D7CE)),
        Style("script", "Каллиграфия", plain(0x1D4D0, 0x1D4EA, 0x1D7CE)),
        Style("fraktur", "Готика", plain(0x1D56C, 0x1D586, 0x1D7CE)),
        Style(
            "double", "Двойной", plain(
                0x1D538, 0x1D552, 0x1D7D8,
                mapOf('C' to "ℂ", 'H' to "ℍ", 'N' to "ℕ", 'P' to "ℙ", 'Q' to "ℚ", 'R' to "ℝ", 'Z' to "ℤ"),
            ),
        ),
        Style("sans", "Без засечек", plain(0x1D5D4, 0x1D5EE, 0x1D7EC)),
        Style("mono", "Моноширинный", plain(0x1D670, 0x1D68A, 0x1D7F6)),
        Style(
            "circle", "Круглый", withUmlauts { c ->
                when (c) {
                    in 'A'..'Z' -> cp(0x24B6 + (c - 'A'))
                    in 'a'..'z' -> cp(0x24D0 + (c - 'a'))
                    '0' -> cp(0x24EA)
                    in '1'..'9' -> cp(0x2460 + (c - '1'))
                    else -> null
                }
            },
        ),
        Style("wide", "Широкий", plain(0xFF21, 0xFF41, 0xFF10)),
        Style("strike", "Зачёркнутый", combining("̶")),
        Style("underline", "Подчёркнутый", combining("̲")),
    )

    private val SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"

    /** Less common looks, shown on their own "Extra" tab. */
    val extra: List<Style> = listOf(
        Style("small_caps", "Малые капитель", withUmlauts { c ->
            when (c.lowercaseChar()) {
                in 'a'..'z' -> SMALL_CAPS[c.lowercaseChar() - 'a'].toString()
                else -> null
            }
        }),
        Style("squared", "Квадратный", withUmlauts { c ->
            when (c) {
                in 'A'..'Z' -> cp(0x1F130 + (c - 'A'))
                in 'a'..'z' -> cp(0x1F130 + (c - 'a'))
                else -> null
            }
        }),
        Style("neg_squared", "Чёрный квадрат", withUmlauts { c ->
            when (c) {
                in 'A'..'Z' -> cp(0x1F170 + (c - 'A'))
                in 'a'..'z' -> cp(0x1F170 + (c - 'a'))
                else -> null
            }
        }),
        Style("neg_circle", "Чёрный круг", withUmlauts { c ->
            when (c) {
                in 'A'..'Z' -> cp(0x1F150 + (c - 'A'))
                in 'a'..'z' -> cp(0x1F150 + (c - 'a'))
                else -> null
            }
        }),
        Style("paren", "В скобках", withUmlauts { c ->
            when (c) {
                in 'A'..'Z' -> cp(0x249C + (c - 'A'))
                in 'a'..'z' -> cp(0x249C + (c - 'a'))
                else -> null
            }
        }),
    )

    fun byId(id: String?): Style? = (all + extra).firstOrNull { it.id == id }

    fun parseFavorites(list: String): List<String> = list.split(',').map { it.trim() }.filter { byId(it) != null }
}
