package com.rob.aikeyboard

import com.rob.aikeyboard.text.FontStyles
import com.rob.aikeyboard.text.Translit
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslitAndExtraFontsTest {
    @Test fun russianToLatin() {
        assertEquals("privet", Translit.toLatin("привет"))
        assertEquals("Shchuka", Translit.toLatin("Щука"))
        assertEquals("Yozh", Translit.toLatin("Ёж"))
        assertEquals("obyom", Translit.toLatin("объём"))
        assertEquals("kak dela? 123", Translit.toLatin("как дела? 123"))
    }

    @Test fun latinAndPunctuationPassThrough() {
        assertEquals("Hello, world!", Translit.toLatin("Hello, world!"))
    }

    @Test fun translitThenFont() {
        val script = FontStyles.byId("script")!!
        assertEquals("𝓹𝓻𝓲𝓿𝓮𝓽", script.apply(Translit.toLatin("привет")))
    }

    @Test fun extraStyles() {
        assertEquals("ʜᴇʟʟᴏ", FontStyles.byId("small_caps")!!.apply("Hello"))
        assertEquals("🄷🄸", FontStyles.byId("squared")!!.apply("hi"))
        assertEquals("⒣⒤", FontStyles.byId("paren")!!.apply("hi"))
    }

    @Test fun favoritesIgnoreUnknownIds() {
        assertEquals(listOf("bold", "squared"), FontStyles.parseFavorites("bold, nope,squared,"))
    }
}
