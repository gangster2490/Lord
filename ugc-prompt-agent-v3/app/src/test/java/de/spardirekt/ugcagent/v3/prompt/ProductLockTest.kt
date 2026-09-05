package de.spardirekt.ugcagent.v3.prompt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductLockTest {
    @Test
    fun injectsLockWhenMissing() {
        val out = ProductLock.ensure("A person picks up the referenced product.", true)
        assertTrue(out.contains("REFERENCE IMAGE OVERRIDES"))
        assertTrue(out.contains("Do not redesign"))
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
    }

    @Test
    fun flagsProductRebuildLanguage() {
        assertTrue(ProductLock.looksLikeProductRebuild("black metal round pan with wooden handle"))
        assertFalse(ProductLock.looksLikeProductRebuild("the referenced product sits on the table"))
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
