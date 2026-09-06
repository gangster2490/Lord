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
        val veo = ProductLock.applyGenerator("x", "VEO")
        assertTrue(veo.contains("8.0 seconds"))
        assertTrue(ProductLock.veoHasExactDuration(veo))
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
        assertTrue(out.contains("STYLE:").not())
        assertTrue(out.contains("Warm, homely"))
        assertTrue(out.contains("PRODUCT IDENTITY LOCK"))
        assertFalse(out.contains("FINAL IDENTITY LOCK"))
        assertEquals(0, ProductLock.leftoverDurationCount(out))
        assertTrue(out.contains("TIMING:"))
        org.junit.Assert.assertEquals(emptyList<String>(), ProductLock.regressionFailures(out, ProductIdentity.microwaveCoverFingerprint(), "VEO", "DEUTSCH"))
    }

    @Test
    fun finalizeCleanOmitsSellerAndConflictingDimensions() {
        val raw = """
FORMAT:
Vertical 9:16.
ACTION:
Place the 28 cm cover on a plate.
Exact dimensions: 28 cm diameter from listing graphic.
""".trimIndent()
        val analysis = org.json.JSONObject()
            .put("product_category", "kitchen")
            .put("dimensions", org.json.JSONArray().put("28 cm").put("30 cm listing"))
            .put("text_claims", org.json.JSONArray().put("28 cm microwave cover"))
        val out = ProductLock.finalizeClean(
            raw,
            ProductIdentity.microwaveCoverFingerprint(),
            "VEO",
            "РУССКИЙ",
            "Вот такую вещь приятно иметь дома.",
            true,
            analysis,
        )
        assertFalse(out.contains("28 cm"))
        assertFalse(out.contains("30 cm"))
        assertFalse(Regex("(?im)^DURATION:").containsMatchIn(out))
        assertFalse(Regex("(?im)^STYLE:").containsMatchIn(out))
        assertFalse(Regex("(?im)^FINAL IDENTITY LOCK:").containsMatchIn(out))
        PromptComposer.CANONICAL_HEADINGS.forEach { heading ->
            assertEquals(heading, 1, PromptComposer.headingCounts(out)[heading] ?: 0)
        }
        assertTrue(out.contains("Вот такую вещь приятно иметь дома."))
        assertEquals(1, ProductLock.extractSpokenHooks(out).size)
    }

    @Test
    fun panIdentityKeepsAllDistinctivePartsAndDropsForeignTemplateTerms() {
        val dirty = """
FORMAT:
Vertical 9:16.
ANTI-MORPH:
No product redesign, invented reservoirs, steam vent, battery, motor, hinges or clips.
ACTION:
One hand slides the pan on the stove.
""".trimIndent()
        val analysis = org.json.JSONObject().put("product_category", "kitchen").put("observed_use_case", "frying pan")
        val out = ProductLock.finalizeClean(
            dirty,
            ProductIdentity.cookwarePanFingerprint(),
            "VEO",
            "РУССКИЙ",
            null,
            true,
            analysis,
        )
        listOf(
            "pan body",
            "rounded deep sidewall",
            "side handle assembly",
            "short metal tang",
            "wooden handle grip",
            "collar",
            "hanging ring",
            "wooden lid",
            "raised lid handle",
            "fastener",
        ).forEach { part ->
            assertTrue(part, out.contains(part, ignoreCase = true))
        }
        assertFalse(out.contains("reservoir", ignoreCase = true))
        assertFalse(out.contains("steam vent", ignoreCase = true))
        assertFalse(out.contains("battery", ignoreCase = true))
        assertFalse(out.contains("motor", ignoreCase = true))
        assertTrue(PromptComposer.endsWithTiming(out))
        assertEquals(1, PromptComposer.durationPhraseCount(out))
        assertEquals(1, ProductLock.antiMorphHeadingCount(out))
        assertEquals(1, ProductLock.extractSpokenHooks(out).size)
        PromptComposer.CANONICAL_HEADINGS.forEach { heading ->
            assertEquals(heading, 1, PromptComposer.headingCounts(out)[heading] ?: 0)
        }
        org.junit.Assert.assertEquals(
            emptyList<String>(),
            ProductLock.regressionFailures(out, ProductIdentity.cookwarePanFingerprint(), "VEO", "РУССКИЙ"),
        )
    }
}
