package com.rob.aikeyboard

import com.rob.aikeyboard.text.FontStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FontStylesTest {
    private fun style(id: String) = FontStyles.byId(id)!!

    @Test fun boldLatinAndDigits() {
        assertEquals("𝐇𝐞𝐥𝐥𝐨 𝟏𝟐𝟑", style("bold").apply("Hello 123"))
    }

    @Test fun scriptAndDoubleStruckExceptions() {
        assertEquals("𝓗𝓮𝓵𝓵𝓸", style("script").apply("Hello"))
        assertEquals("ℂℍℕ", style("double").apply("CHN"))
        assertEquals("ℎ𝑖", style("italic").apply("hi"))
    }

    @Test fun circledDigits() {
        assertEquals("Ⓐⓑ①⑨⓪", style("circle").apply("Ab190"))
    }

    @Test fun umlautsGetDiaeresis() {
        assertEquals("𝐚̈𝐒", style("bold").apply("äS"))
        assertEquals("ß", style("bold").apply("ß"))
    }

    @Test fun cyrillicUnchangedExceptLineStyles() {
        assertEquals("привет", style("bold").apply("привет"))
        assertEquals("п̶р̶", style("strike").apply("пр"))
        assertEquals("a̲ b̲", style("underline").apply("a b"))
    }

    @Test fun everyStyleHasASampleAndPunctuationIsKept() {
        FontStyles.all.forEach {
            assertNotNull(it.sample)
            assertEquals(", !?", it.apply(", !?"))
        }
    }
}
