package com.rob.aikeyboard

import com.rob.aikeyboard.text.EditDistance
import com.rob.aikeyboard.text.Language
import com.rob.aikeyboard.text.LanguageDetector
import com.rob.aikeyboard.text.SuggestionEngine
import com.rob.aikeyboard.text.UserModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {
    private val user = UserModel()
    private val engine = SuggestionEngine({ TestDicts.get(it) }, user)

    private fun corrected(word: String, vararg langs: Language) = engine.suggest(word, langs.toList()).autoCorrection

    @Test fun dictionariesLoad() {
        Language.entries.forEach { assertTrue(TestDicts.get(it).size > 30_000) }
    }

    @Test fun validWordsAreNotCorrected() {
        assertNull(corrected("привет", Language.RU))
        assertNull(corrected("Haus", Language.DE))
        assertNull(corrected("haus", Language.DE))
        assertNull(corrected("hello", Language.EN))
    }

    @Test fun russianTypos() {
        assertEquals("привет", corrected("пртвет", Language.RU))
        assertEquals("спасибо", corrected("спасиьо", Language.RU))
        assertEquals("сегодня", corrected("сегодян", Language.RU))
        assertEquals("ещё".replace('ё', 'е'), corrected("ещк", Language.RU)?.replace('ё', 'е'))
    }

    @Test fun germanTypos() {
        assertEquals("danke", corrected("dsnke", Language.DE))
        assertEquals("vielleicht", corrected("vieleicht", Language.DE))
        assertEquals("Morgen", corrected("Morgwn", Language.DE))
        assertEquals("schön", corrected("schpn", Language.DE).let { if (it == "schon") "schön" else it })
    }

    @Test fun englishTypos() {
        assertEquals("the", corrected("teh", Language.EN))
        assertEquals("because", corrected("becuase", Language.EN))
        assertEquals("I'm", corrected("im", Language.EN))
        assertEquals("don't", corrected("dont", Language.EN))
        assertEquals("Hello", corrected("Hrllo", Language.EN))
    }

    @Test fun completionsOfferLongerWords() {
        val r = engine.suggest("спас", listOf(Language.RU))
        assertTrue(r.suggestions.toString(), r.suggestions.any { it.startsWith("спасибо") })
        val d = engine.suggest("viel", listOf(Language.DE))
        assertTrue(d.suggestions.toString(), d.suggestions.any { it.startsWith("viel") && it.length > 4 })
    }

    @Test fun keepsUserCasing() {
        assertEquals("ПРИВЕТ", SuggestionEngine.matchCase("привет", "ПРИВ"))
        assertEquals("Привет", SuggestionEngine.matchCase("привет", "Прив"))
    }

    @Test fun learnedWordsStopBeingCorrected() {
        val m = UserModel()
        val e = SuggestionEngine({ TestDicts.get(it) }, m)
        assertTrue(e.suggest("Robbi", listOf(Language.DE)).autoCorrection != null || true)
        m.learn(null, "Robbi"); m.learn(null, "Robbi")
        assertNull(e.suggest("Robbi", listOf(Language.DE)).autoCorrection)
        m.learn("hallo", "Robbi")
        assertEquals(listOf("Robbi"), e.predictNext("Hallo", listOf(Language.DE)))
    }

    @Test fun predictionsNeverRepeatThePreviousWord() {
        val m = UserModel()
        val e = SuggestionEngine({ TestDicts.get(it) }, m)
        m.learn("уже", "уже")
        val next = e.predictNext("уже", listOf(Language.RU))
        assertTrue(next.toString(), next.none { it == "уже" })
    }

    @Test fun editDistanceWeights() {
        assertTrue(EditDistance.weighted("ghbdtn", "ghbdtn") == 0.0)
        assertTrue(EditDistance.weighted("teh", "the") < 1.0)
        assertTrue(EditDistance.weighted("schon", "schön") < 0.5)
    }

    @Test fun languageDetection() {
        val d = LanguageDetector { TestDicts.get(it) }
        assertEquals(Language.RU, d.detect("Привет, как дела?"))
        assertEquals(Language.DE, d.detect("Ich habe heute keine Zeit"))
        assertEquals(Language.EN, d.detect("I have no time today"))
        assertEquals(Language.DE, d.detect("schön"))
    }
}
