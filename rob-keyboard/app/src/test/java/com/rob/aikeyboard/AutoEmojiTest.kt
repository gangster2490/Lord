package com.rob.aikeyboard

import com.rob.aikeyboard.emoji.AutoEmoji
import com.rob.aikeyboard.emoji.AutoEmoji.Placement
import com.rob.aikeyboard.emoji.EmojiCatalog
import com.rob.aikeyboard.emoji.EmojiHistory
import com.rob.aikeyboard.emoji.EmojiIntensity
import com.rob.aikeyboard.text.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEmojiTest {
    private val auto = AutoEmoji()

    /** Simulates typing [text] word by word, applying insertions like the keyboard service does. */
    private fun type(text: String, intensity: EmojiIntensity = EmojiIntensity.NORMAL, lang: Language = Language.RU): String {
        val out = StringBuilder()
        for (ch in text) {
            val isSeparator = !ch.isLetter() && ch != '\''
            if (isSeparator && out.isNotEmpty() && out.last().isLetter()) {
                val ins = auto.onWordFinished(out.toString(), ch, intensity, lang)
                when (ins?.placement) {
                    Placement.BEFORE_SEPARATOR -> { out.append(' ').append(ins.emoji); out.append(ch) }
                    Placement.AFTER_SEPARATOR -> { out.append(ch).append(' ').append(ins.emoji) }
                    null -> out.append(ch)
                }
            } else out.append(ch)
        }
        return out.toString()
    }

    @Test fun specExample() {
        assertEquals("Привет 👋, как дела? 😊 Желаю хорошего дня! ☀️", type("Привет, как дела? Желаю хорошего дня!"))
    }

    @Test fun germanAndEnglish() {
        assertEquals("Hallo 👋 wie geht's? 😊", type("Hallo wie geht's?", lang = Language.DE))
        assertEquals("Thank you 🙏 and good night. 🌙", type("Thank you and good night.", lang = Language.EN))
    }

    @Test fun neverInsideWordsAndNoRepeats() {
        assertEquals("Приветствие", type("Приветствие"))
        assertEquals("Привет 👋 привет", type("Привет привет "). trimEnd())
    }

    @Test fun intensityControlsWeakWords() {
        assertNull(auto.onWordFinished("Пью кофе", ' ', EmojiIntensity.LOW, Language.RU))
        assertEquals("☕", auto.onWordFinished("Пью кофе", ' ', EmojiIntensity.NORMAL, Language.RU)?.emoji)
        assertNull(auto.onWordFinished("Мой кот", ' ', EmojiIntensity.NORMAL, Language.RU))
        assertEquals("🐱", auto.onWordFinished("Мой кот", ' ', EmojiIntensity.HIGH, Language.RU)?.emoji)
    }

    @Test fun laughter() {
        assertEquals("😂", auto.onWordFinished("хахаха", ' ', EmojiIntensity.LOW, Language.RU)?.emoji)
        assertEquals("😂", auto.onWordFinished("hahaha", ' ', EmojiIntensity.LOW, Language.EN)?.emoji)
    }

    @Test fun emojiCatalogLoadsAndSearchesInAllLanguages() {
        val c = TestDicts.emojiFile().inputStream().use { EmojiCatalog.load(it) }
        assertTrue(c.items.size > 1500)
        assertTrue(c.search("heart").any { it.emoji == "❤️" })
        assertTrue(c.search("herz").any { it.emoji == "❤️" })
        assertTrue(c.search("сердце").any { it.emoji == "❤️" })
        assertTrue(c.find("👋")!!.skinTones.size == 5)
    }

    @Test fun emojiHistoryRoundTrip() {
        val h = EmojiHistory()
        h.use("😀"); h.use("👍"); h.use("😀"); h.toggleFavorite("❤️")
        val r = EmojiHistory.deserialize(h.serialize())
        assertEquals(listOf("😀", "👍"), r.recent())
        assertEquals(listOf("😀"), r.frequent())
        assertEquals(listOf("❤️"), r.favorites())
    }
}

class AutoEmojiChatWordsTest {
    private val auto = AutoEmoji()

    @org.junit.Test fun commonChatWords() {
        assertEquals("😋", auto.onWordFinished("Как вкусно", '!', EmojiIntensity.NORMAL, Language.RU)?.emoji)
        assertEquals("😍", auto.onWordFinished("ты красавица", ' ', EmojiIntensity.NORMAL, Language.RU)?.emoji)
        assertEquals(null, auto.onWordFinished("Нет", ' ', EmojiIntensity.NORMAL, Language.RU))
        assertEquals("🙅", auto.onWordFinished("Нет", ' ', EmojiIntensity.HIGH, Language.RU)?.emoji)
        assertEquals("😋", auto.onWordFinished("Das ist lecker", ' ', EmojiIntensity.NORMAL, Language.DE)?.emoji)
    }
}
