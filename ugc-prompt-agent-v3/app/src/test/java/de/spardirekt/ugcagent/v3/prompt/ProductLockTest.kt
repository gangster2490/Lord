package de.spardirekt.ugcagent.v3.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductLockTest {
    @Test
    fun injectsLockWhenMissing() {
        val out = ProductLock.ensure("A person picks up the referenced product.", true)
        assertTrue(out.contains("REFERENCE IMAGE OVERRIDES"))
        assertTrue(out.contains("Do not redesign"))
        assertTrue(out.contains("Do not generate a similar product"))
        assertFalse(ProductLock.looksLikeProductRebuild(out))
        assertFalse(ProductLock.allowsGenericSubstitution(out))
    }

    @Test
    fun doesNotDuplicateLock() {
        val once = ProductLock.ensure("REFERENCE IMAGE OVERRIDES TEXTUAL INTERPRETATION.\nHello", true)
        assertEquals(1, Regex("REFERENCE IMAGE OVERRIDES").findAll(once).count())
    }

    @Test
    fun speechOffAddsLine() {
        val out = ProductLock.ensureNoSpeech("clip")
        assertTrue(out.contains("No spoken dialogue."))
    }

    @Test
    fun generatorNotes() {
        assertTrue(ProductLock.applyGenerator("x", "VEO").contains("Veo"))
        assertTrue(ProductLock.applyGenerator("x", "KLING").contains("Kling"))
        assertTrue(ProductLock.applyGenerator("x", "GENERIC").contains("generic"))
        assertTrue(ProductLock.applyGenerator("x", "VEO").contains("exactly 8.0 seconds"))
    }

    @Test
    fun flagsGenericSubstitutionLanguage() {
        assertTrue(ProductLock.looksLikeProductRebuild("use a similar product from the same category"))
        assertFalse(ProductLock.looksLikeProductRebuild("the referenced product sits on the table"))
    }

    @Test
    fun collapseDuplicateSpeechHeadingAndTiming() {
        val raw = """
FORMAT:
Vertical 9:16.
SPEECH:
Old line.
The spoken line must finish before the 8.0-second endpoint.
SPEECH:
Another line.
The spoken line must finish before the 8.0-second endpoint.
""".trimIndent()
        val out = ProductLock.normalizeSpeech(raw, "DEUTSCH", "Keine Lust, die Mikrowelle nach jedem Aufwärmen zu putzen?")
        assertEquals(1, ProductLock.speechHeadingCount(out))
        assertEquals(1, ProductLock.speechEndTimingCount(out))
        assertTrue(out.contains("Keine Lust"))
        org.junit.Assert.assertEquals(emptyList<String>(), ProductLock.regressionFailures(out, null, "VEO", "DEUTSCH").filter { it.startsWith("duplicate_speech") })
    }
}
