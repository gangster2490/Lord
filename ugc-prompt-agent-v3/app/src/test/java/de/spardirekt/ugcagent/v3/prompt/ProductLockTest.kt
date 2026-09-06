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

    @Test
    fun finalizeCleanDedupesIdentityMovingSpeechDurationAndConflictingHooks() {
        val raw = """
Target generator: Veo. Vertical 9:16. One continuous clip.
FINAL IDENTITY LOCK:
1. old lock
FINAL IDENTITY LOCK:
2. another lock
MOVING COMPONENT LOCK:
Keep parts moving even if uncertain.
MOVING COMPONENT LOCK:
Stretch the handle.
DURATION:
Generate exactly 8.0 seconds total.
DURATION:
maximum 8 seconds
SPEECH:
"Kurz den Deckel auf den Teller."
SPEECH:
"Das ist das beste Produkt ever."
The spoken line must finish before the 8.0-second endpoint.
The spoken line must finish before the 8.0-second endpoint.
""".trimIndent()
        val out = ProductLock.finalizeClean(
            raw,
            ProductIdentity.microwaveCoverFingerprint(),
            "VEO",
            "DEUTSCH",
            "Ich mag's, wenn in der Küche alles einfach und gemütlich bleibt.",
            true,
            org.json.JSONObject().put("product_category", "kitchen").put("observed_use_case", "cover food"),
        )
        assertEquals(1, ProductLock.identityLockCount(out))
        assertEquals(1, ProductLock.movingLockCount(out))
        assertEquals(1, ProductLock.speechHeadingCount(out))
        assertEquals(1, ProductLock.durationHeadingCount(out))
        assertEquals(1, ProductLock.speechEndTimingCount(out))
        assertFalse(ProductLock.hasConflictingSpokenHooks(out))
        assertTrue(out.contains("Ich mag's") || out.contains("gemütlich") || out.contains("Hause"))
        assertFalse(out.contains("beste Produkt"))
        assertTrue(out.contains("STYLE:"))
        assertTrue(out.contains("Warm, homely"))
    }
}
