package com.rob.aikeyboard.emoji

import com.rob.aikeyboard.text.Language
import java.util.Locale

enum class EmojiIntensity(val maxStrength: Int) { LOW(1), NORMAL(2), HIGH(3) }

/**
 * Local, rule-based "Auto Emoji": after a word or phrase is finished, decides whether a fitting
 * emoji should follow it. Nothing leaves the device.
 */
class AutoEmoji(private val rules: List<Rule> = DEFAULT_RULES) {
    private val regexCache = HashMap<String, Regex>()

    /** One pattern token: exact word, `prefix*`, or `~regex`. */
    data class Rule(val language: Language, val tokens: List<String>, val emoji: String, val strength: Int)

    enum class Placement {
        /** `word 👋,` — emoji goes between the word and a comma/space. */
        BEFORE_SEPARATOR,
        /** `word? 😊` — emoji follows sentence-ending punctuation. */
        AFTER_SEPARATOR,
    }

    data class Insertion(val emoji: String, val placement: Placement)

    /**
     * @param textBefore text up to and including the word that was just finished (no separator).
     * @param separator the character that finished the word (space or punctuation).
     */
    fun onWordFinished(
        textBefore: String,
        separator: Char,
        intensity: EmojiIntensity,
        language: Language?,
    ): Insertion? {
        if (textBefore.isEmpty() || !textBefore.last().isLetter()) return null
        val tokens = WORD.findAll(textBefore.takeLast(80)).map { it.value.lowercase(Locale.ROOT) }.toList()
        if (tokens.isEmpty()) return null

        val match = rules.asSequence()
            .filter { it.strength <= intensity.maxStrength }
            .filter { language == null || it.language == language || it.tokens.size > 1 }
            .filter { endsWith(tokens, it.tokens) }
            // Longer phrases are more specific than single words.
            .sortedWith(compareByDescending<Rule> { it.tokens.size }.thenBy { it.strength })
            .firstOrNull() ?: return null

        if (!allowed(textBefore, match.emoji, intensity)) return null
        val placement = if (separator in ".!?…") Placement.AFTER_SEPARATOR else Placement.BEFORE_SEPARATOR
        return Insertion(match.emoji, placement)
    }

    private fun allowed(textBefore: String, emoji: String, intensity: EmojiIntensity): Boolean {
        val recent = textBefore.takeLast(100)
        if (recent.contains(emoji)) return false
        return when (intensity) {
            EmojiIntensity.LOW -> !containsEmoji(textBefore.takeLast(60))
            EmojiIntensity.NORMAL -> !containsEmoji(lastWords(textBefore, 2))
            EmojiIntensity.HIGH -> !containsEmoji(lastWords(textBefore, 1))
        }
    }

    private fun lastWords(text: String, n: Int): String {
        var count = 0
        var i = text.length - 1
        var inWord = false
        while (i >= 0) {
            val letter = text[i].isLetter()
            if (letter && !inWord) {
                inWord = true
            } else if (!letter && inWord) {
                inWord = false
                count++
                if (count == n) return text.substring(i)
            }
            i--
        }
        return text
    }

    private fun endsWith(tokens: List<String>, pattern: List<String>): Boolean {
        if (pattern.size > tokens.size) return false
        val offset = tokens.size - pattern.size
        return pattern.indices.all { tokenMatches(tokens[offset + it], pattern[it]) }
    }

    private fun tokenMatches(token: String, pattern: String): Boolean = when {
        pattern.startsWith("~") -> regexCache.getOrPut(pattern) { Regex(pattern.substring(1)) }.matches(token)
        pattern.endsWith("*") -> token.startsWith(pattern.dropLast(1))
        else -> token == pattern
    }

    companion object {
        private val WORD = Regex("[\\p{L}']+")

        fun containsEmoji(text: String): Boolean {
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (isEmojiCodePoint(cp)) return true
                i += Character.charCount(cp)
            }
            return false
        }

        fun isEmojiCodePoint(cp: Int): Boolean =
            cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x1F000..0x1F2FF || cp == 0x2B50 || cp == 0x2764

        private fun rules(language: Language, vararg entries: Triple<String, String, Int>) =
            entries.map { (pattern, emoji, strength) -> Rule(language, pattern.split(' '), emoji, strength) }

        private infix fun Pair<String, String>.s(strength: Int) = Triple(first, second, strength)

        val DEFAULT_RULES: List<Rule> = rules(
            Language.RU,
            "привет*" to "👋" s 1, "здравствуй*" to "👋" s 1, "пока" to "👋" s 1, "до свидания" to "👋" s 1,
            "как дела" to "😊" s 1, "как ты" to "😊" s 2, "спасиб*" to "🙏" s 1, "благодар*" to "🙏" s 1,
            "люблю" to "❤️" s 1, "обнимаю" to "🤗" s 1, "целую" to "😘" s 1,
            "хорош* дн*" to "☀️" s 1, "добр* утр*" to "☀️" s 1, "спокойной ночи" to "🌙" s 1, "добр* ночи" to "🌙" s 1,
            "сладких снов" to "😴" s 1, "днём рождения" to "🎂" s 1, "днем рождения" to "🎂" s 1,
            "поздравля*" to "🎉" s 1, "ура" to "🎉" s 1, "~(ха){2,}х?" to "😂" s 1, "смешно" to "😂" s 2,
            "круто" to "😎" s 2, "супер" to "👍" s 2, "отлично" to "👍" s 2, "класс" to "👍" s 2, "ок" to "👌" s 2,
            "окей" to "👌" s 2, "грустн*" to "😢" s 2, "жаль" to "😔" s 2, "извини*" to "🙏" s 2, "прости*" to "🙏" s 2,
            "кофе" to "☕" s 2, "чай" to "🍵" s 3, "пицц*" to "🍕" s 2, "торт*" to "🎂" s 3, "пиво" to "🍺" s 3,
            "вино" to "🍷" s 3, "обед*" to "🍽️" s 3, "ужин*" to "🍽️" s 3, "работ*" to "💼" s 3, "деньг*" to "💰" s 3,
            "машин*" to "🚗" s 3, "домой" to "🏠" s 3, "отпуск*" to "🏖️" s 2, "море" to "🌊" s 3, "солнц*" to "☀️" s 3,
            "дожд*" to "🌧️" s 3, "снег*" to "❄️" s 3, "холодно" to "🥶" s 2, "жарко" to "🥵" s 2, "кошк*" to "🐱" s 3,
            "кот" to "🐱" s 3, "собак*" to "🐶" s 3, "музык*" to "🎵" s 3, "футбол*" to "⚽" s 3, "подар*" to "🎁" s 2,
            "цвет*" to "💐" s 3, "удачи" to "🍀" s 1, "молодец" to "👏" s 1, "браво" to "👏" s 1, "вау" to "😮" s 2,
            "ого" to "😮" s 2, "устал*" to "😴" s 2, "болею" to "🤒" s 2, "выздоравлива*" to "💪" s 1,
            "праздник*" to "🎉" s 2, "новым годом" to "🎄" s 1, "рождеств*" to "🎄" s 2, "скучаю" to "🥺" s 1,
            "думаю" to "🤔" s 3, "счастлив*" to "😊" s 2, "рад" to "😊" s 2, "рада" to "😊" s 2, "огонь" to "🔥" s 2,
        ) + rules(
            Language.DE,
            "hallo" to "👋" s 1, "hi" to "👋" s 1, "hey" to "👋" s 1, "tschüss" to "👋" s 1, "tschüs" to "👋" s 1,
            "bis bald" to "👋" s 1, "wie geht's" to "😊" s 1, "wie gehts" to "😊" s 1, "geht es dir" to "😊" s 1,
            "danke*" to "🙏" s 1, "liebe dich" to "❤️" s 1, "dich lieb" to "❤️" s 1, "umarmung*" to "🤗" s 1,
            "kuss" to "😘" s 1, "küsse" to "😘" s 1, "schönen tag" to "☀️" s 1, "guten morgen" to "☀️" s 1,
            "gute nacht" to "🌙" s 1, "schlaf gut" to "😴" s 1, "alles gute" to "🎉" s 1, "glückwunsch*" to "🎉" s 1,
            "geburtstag*" to "🎂" s 1, "~(ha){2,}h?" to "😂" s 1, "lol" to "😂" s 1, "lustig" to "😂" s 2,
            "cool" to "😎" s 2, "super" to "👍" s 2, "toll" to "👍" s 2, "prima" to "👍" s 2, "perfekt" to "👌" s 2,
            "ok" to "👌" s 2, "okay" to "👌" s 2, "traurig" to "😢" s 2, "schade" to "😔" s 2, "sorry" to "🙏" s 2,
            "entschuldigung" to "🙏" s 2, "kaffee" to "☕" s 2, "tee" to "🍵" s 3, "pizza" to "🍕" s 2,
            "kuchen" to "🍰" s 3, "bier" to "🍺" s 3, "wein" to "🍷" s 3, "essen" to "🍽️" s 3, "arbeit" to "💼" s 3,
            "geld" to "💰" s 3, "auto" to "🚗" s 3, "nach hause" to "🏠" s 3, "urlaub" to "🏖️" s 2, "meer" to "🌊" s 3,
            "sonne" to "☀️" s 3, "regen" to "🌧️" s 3, "schnee" to "❄️" s 3, "kalt" to "🥶" s 2, "heiß" to "🥵" s 2,
            "katze*" to "🐱" s 3, "hund*" to "🐶" s 3, "musik" to "🎵" s 3, "fußball" to "⚽" s 3,
            "geschenk*" to "🎁" s 2, "blumen" to "💐" s 3, "viel glück" to "🍀" s 1, "gut gemacht" to "👏" s 1,
            "bravo" to "👏" s 1, "wow" to "😮" s 2, "müde" to "😴" s 2, "krank" to "🤒" s 2,
            "gute besserung" to "💪" s 1, "frohe weihnachten" to "🎄" s 1, "frohes neues" to "🎆" s 1,
            "vermisse dich" to "🥺" s 1, "glücklich" to "😊" s 2, "freue mich" to "😊" s 2, "party" to "🎉" s 2,
        ) + rules(
            Language.EN,
            "hello" to "👋" s 1, "hi" to "👋" s 1, "hey" to "👋" s 1, "bye" to "👋" s 1, "goodbye" to "👋" s 1,
            "see you" to "👋" s 1, "how are you" to "😊" s 1, "thanks" to "🙏" s 1, "thank you" to "🙏" s 1, "thx" to "🙏" s 1,
            "love you" to "❤️" s 1, "hugs" to "🤗" s 1, "kisses" to "😘" s 1, "nice day" to "☀️" s 1,
            "great day" to "☀️" s 1, "good day" to "☀️" s 1, "good morning" to "☀️" s 1, "good night" to "🌙" s 1,
            "sweet dreams" to "😴" s 1, "happy birthday" to "🎂" s 1, "congrat*" to "🎉" s 1,
            "~(ha){2,}h?" to "😂" s 1, "lol" to "😂" s 1, "funny" to "😂" s 2, "cool" to "😎" s 2,
            "awesome" to "👍" s 2, "great" to "👍" s 2, "perfect" to "👌" s 2, "ok" to "👌" s 2, "okay" to "👌" s 2,
            "sad" to "😢" s 2, "sorry" to "🙏" s 2, "coffee" to "☕" s 2, "tea" to "🍵" s 3, "pizza" to "🍕" s 2,
            "cake" to "🍰" s 3, "beer" to "🍺" s 3, "wine" to "🍷" s 3, "lunch" to "🍽️" s 3, "dinner" to "🍽️" s 3,
            "work" to "💼" s 3, "money" to "💰" s 3, "car" to "🚗" s 3, "home" to "🏠" s 3, "vacation" to "🏖️" s 2,
            "holiday" to "🏖️" s 2, "beach" to "🏖️" s 3, "sunny" to "☀️" s 3, "rain" to "🌧️" s 3, "snow" to "❄️" s 3,
            "cold" to "🥶" s 2, "hot" to "🥵" s 2, "cat" to "🐱" s 3, "dog" to "🐶" s 3, "music" to "🎵" s 3,
            "football" to "⚽" s 3, "soccer" to "⚽" s 3, "gift" to "🎁" s 2, "flowers" to "💐" s 3,
            "good luck" to "🍀" s 1, "well done" to "👏" s 1, "wow" to "😮" s 2, "tired" to "😴" s 2,
            "sick" to "🤒" s 2, "get well" to "💪" s 1, "merry christmas" to "🎄" s 1, "new year" to "🎆" s 1,
            "miss you" to "🥺" s 1, "happy" to "😊" s 2, "glad" to "😊" s 2, "party" to "🎉" s 2,
        )
    }
}
