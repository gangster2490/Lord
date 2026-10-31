package com.rob.aikeyboard.text

/** Russian (and Ukrainian) letters → Latin, the way people write "privet" in chats. Other characters pass through. */
object Translit {
    private val MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu",
        'я' to "ya", 'і' to "i", 'ї' to "yi", 'є' to "ye", 'ґ' to "g",
    )

    fun toLatin(text: String): String = buildString {
        text.forEach { c ->
            val latin = MAP[c.lowercaseChar()]
            when {
                latin == null -> append(c)
                c.isUpperCase() -> append(latin.replaceFirstChar { it.uppercaseChar() })
                else -> append(latin)
            }
        }
    }
}
