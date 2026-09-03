package de.spardirekt.veoprompt.ultra.compliance

import de.spardirekt.veoprompt.ultra.generation.Fixtures
import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiVeoPromptSanitizerTest {

    private val pan = ProductModel(
        productIdentity = "Deep black pan with wooden lid",
        visualSignature = listOf("deep rounded bowl", "wooden handle", "wooden lid")
    )

    @Test
    fun catalogHasGeminiCodes() {
        val codes = GeminiVeoPolicy.RULES.map { it.code }.toSet()
        assertTrue(
            codes.containsAll(
                listOf(
                    "GV_SUBMIT",
                    "GV_NO_MINORS_UNSAFE",
                    "GV_NO_SEXUAL",
                    "GV_NO_NUDITY",
                    "GV_NO_REAL_PERSON",
                    "GV_NO_VIOLENCE",
                    "GV_NO_WEAPONS",
                    "GV_NO_SELF_HARM",
                    "GV_NO_HATE",
                    "GV_NO_DRUGS",
                    "GV_NO_COPYRIGHT_CHAR",
                    "GV_NO_CHILD_TALENT"
                )
            )
        )
        assertEquals("2026.09-v1", GeminiVeoPolicy.VERSION)
        assertTrue("GV_NO_MINORS_UNSAFE" in GeminiVeoPolicy.HARD_BLOCK_CODES)
        assertTrue("GV_NO_WEAPONS" in GeminiVeoPolicy.HARD_BLOCK_CODES)
        assertTrue("GV_NO_SELF_HARM" in GeminiVeoPolicy.HARD_BLOCK_CODES)
    }

    @Test
    fun cleanPackageIsReadyAndKeepsIdentity() {
        val original = Fixtures.validVeoPrompt(pan)
        val result = GeminiVeoPromptSanitizer.sanitize(
            StructuredResponse(
                veoPrompt = original,
                voiceover = "Tiefer Topf mit festem Holzdeckel hält die Hitze.",
                title = "Tiefe Pfanne mit Holzdeckel",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            pan,
            VoiceLanguage.DE
        )
        assertEquals(GeminiVeoComplianceSystem.VERDICT_READY, result.report.verdict)
        assertTrue(result.report.submissionSafe)
        assertTrue(result.response.veoPrompt.length >= original.length)
        assertTrue(result.response.veoPrompt.contains("deep rounded bowl"))
        assertTrue(result.response.veoPrompt.contains("GEMINI / VEO HARD LOCK"))
        assertTrue(result.response.veoPrompt.contains("no celebrity or real-person likeness"))
        assertFalse(result.response.veoPrompt.contains("GEMINI AUDIT"))
        assertFalse(result.response.veoPrompt.contains("SAFETY AUDIT"))
        assertFalse(result.response.veoPrompt.endsWith("..."))
        assertTrue(result.report.checklist.any { it.startsWith("OK · GV_SUBMIT") })
        assertTrue(result.report.checklist.all { it.startsWith("OK") })
    }

    @Test
    fun enrichAppendsWithoutTruncating() {
        val original = Fixtures.validVeoPrompt(pan)
        val enriched = GeminiVeoPromptSanitizer.enrichPrompt(original)
        assertTrue(enriched.length >= original.length)
        assertTrue(enriched.contains("The same single physical product"))
        assertTrue(enriched.contains("GEMINI / VEO HARD LOCK"))
        assertFalse(enriched.endsWith("..."))
        assertFalse(enriched.contains("GEMINI AUDIT"))
        val again = GeminiVeoPromptSanitizer.enrichPrompt(enriched)
        assertEquals(enriched, again)
    }

    @Test
    fun celebrityLookalikeIsSanitized() {
        val original = Fixtures.validVeoPrompt(pan)
        val result = GeminiVeoPromptSanitizer.sanitize(
            StructuredResponse(
                veoPrompt = original,
                voiceover = "Use a celebrity lookalike to hold the pan.",
                title = "Pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE
        )
        assertEquals(GeminiVeoComplianceSystem.VERDICT_SANITIZED, result.report.verdict)
        assertTrue(result.report.submissionSafe)
        assertTrue(result.findings.any { it.code == "GV_NO_REAL_PERSON" })
        assertFalse(result.response.voiceover.contains("celebrity lookalike", ignoreCase = true))
        assertFalse(result.response.veoPrompt.contains("celebrity lookalike", ignoreCase = true))
        assertTrue(result.response.veoPrompt.contains("deep rounded bowl"))
        assertFalse(result.response.veoPrompt.contains("GEMINI AUDIT"))
        assertTrue(result.response.veoPrompt.length >= original.length)
    }

    @Test
    fun goreInShotSequenceIsRewritten() {
        val dirty = Fixtures.validVeoPrompt(pan).replace(
            "4.0–6.0s — FEATURE / DEMO: one hand, one verified action",
            "4.0–6.0s — FEATURE / DEMO: Show gushing blood on the rim, then the same pan."
        )
        val result = GeminiVeoPromptSanitizer.sanitize(
            StructuredResponse(
                veoPrompt = dirty,
                voiceover = "Tiefer Topf, fester Holzdeckel.",
                title = "Pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE
        )
        assertTrue(result.findings.any { it.code == "GV_NO_VIOLENCE" })
        assertFalse(result.response.veoPrompt.contains("gushing blood", ignoreCase = true))
        assertTrue(result.response.veoPrompt.contains("clean dry product surface"))
        assertTrue(result.response.veoPrompt.contains("wooden handle"))
        assertFalse(result.response.veoPrompt.contains("SAFETY AUDIT"))
        assertEquals(GeminiVeoComplianceSystem.VERDICT_SANITIZED, result.report.verdict)
    }

    @Test
    fun childTalentIsRewrittenToAdultHand() {
        val result = GeminiVeoPromptSanitizer.sanitize(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(pan),
                voiceover = "A child model lifts the lid.",
                title = "Pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE
        )
        assertTrue(result.findings.any { it.code == "GV_NO_CHILD_TALENT" })
        assertFalse(result.response.voiceover.contains("child model", ignoreCase = true))
        assertTrue(result.response.voiceover.contains("adult hand", ignoreCase = true))
        assertEquals(GeminiVeoComplianceSystem.VERDICT_SANITIZED, result.report.verdict)
        assertTrue(result.report.submissionSafe)
    }

    @Test
    fun minorsUnsafeStaysBlockedAfterStrip() {
        val result = GeminiVeoPromptSanitizer.sanitize(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(pan),
                voiceover = "Never show a sexualized child near the pan.",
                title = "Pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE
        )
        assertEquals(GeminiVeoComplianceSystem.VERDICT_BLOCKED, result.report.verdict)
        assertFalse(result.report.submissionSafe)
        assertTrue(result.findings.any { it.code == "GV_NO_MINORS_UNSAFE" })
        assertFalse(result.response.voiceover.contains("sexualized child", ignoreCase = true))
        assertFalse(result.response.veoPrompt.contains("sexualized child", ignoreCase = true))
        assertTrue(result.response.veoPrompt.contains("PRODUCT LOCK"))
        assertFalse(result.response.veoPrompt.contains("GEMINI AUDIT"))
        assertTrue(result.report.checklist.any { it.startsWith("FAIL · GV_NO_MINORS_UNSAFE") })
        assertTrue(result.report.publishSteps.any { it.contains("Не вставляйте в Gemini") })
    }

    @Test
    fun weaponInstructionsStayBlocked() {
        val result = GeminiVeoPromptSanitizer.sanitize(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(pan),
                voiceover = "Do not explain how to build a bomb with kitchen parts.",
                title = "Pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE
        )
        assertEquals(GeminiVeoComplianceSystem.VERDICT_BLOCKED, result.report.verdict)
        assertFalse(result.report.submissionSafe)
        assertTrue(result.findings.any { it.code == "GV_NO_WEAPONS" })
        assertFalse(result.response.voiceover.contains("how to build a bomb", ignoreCase = true))
        assertFalse(result.response.veoPrompt.contains("how to build a bomb", ignoreCase = true))
    }

    @Test
    fun overlayCopyrightCharacterIsDropped() {
        val prompt = Fixtures.validVeoPrompt(pan).replace(
            "Holzdeckel\nTiefe Form",
            "disney character\nHolzdeckel"
        )
        val result = GeminiVeoPromptSanitizer.sanitize(
            StructuredResponse(
                veoPrompt = prompt,
                voiceover = "Tiefer Topf, fester Holzdeckel.",
                title = "Pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE
        )
        assertTrue(result.findings.any { it.code == "GV_NO_COPYRIGHT_CHAR" })
        assertFalse(result.response.veoPrompt.contains("disney character", ignoreCase = true))
        assertTrue(result.response.veoPrompt.contains("Holzdeckel") || result.response.veoPrompt.contains("unbranded"))
        assertFalse(result.response.veoPrompt.contains("GEMINI AUDIT"))
    }

    @Test
    fun bloodPressureProductIsNotFalsePositive() {
        val monitor = ProductModel(
            productIdentity = "blood pressure monitor",
            visualSignature = listOf("cuff", "display", "tube")
        )
        val prompt = Fixtures.validVeoPrompt(monitor)
        val result = GeminiVeoPromptSanitizer.sanitize(
            StructuredResponse(
                veoPrompt = prompt,
                voiceover = "Blood pressure cuff sits flat on the table.",
                title = "Blood pressure monitor",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            monitor,
            VoiceLanguage.DE
        )
        assertFalse(result.findings.any { it.code == "GV_NO_VIOLENCE" })
        assertEquals(GeminiVeoComplianceSystem.VERDICT_READY, result.report.verdict)
        assertTrue(result.response.voiceover.contains("Blood pressure", ignoreCase = true))
        assertTrue(result.response.veoPrompt.contains("cuff"))
    }
}
