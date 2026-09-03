package de.spardirekt.veoprompt.ultra.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiVeoPromptCleanupTest {

    @Test
    fun composeStripsSafetyAuditAndLocksTwelveSections() {
        val raw = """
FORMAT
Vertical 9:16. Exactly 8.0 seconds.

REFERENCES
Product photos confirm black frame.

PRODUCT LOCK
Preserve black tubular frame and red tray.
CORE PRINCIPLE: CREATIVE PRESENTATION = FLEXIBLE. PRODUCT DESIGN = LOCKED.

SETTING
Premium studio.

SHOT SEQUENCE
0.0–2.0s — HOOK
2.0–4.0s — IDENTITY
4.0–6.0s — FEATURE / DEMO
6.0–8.0s — HERO / CTA

ON-SCREEN TEXT
Compact fold

VOICEOVER
Fester Rahmen, rotes Tablett.

AUDIO
Subtle click and soft music.

CRITICAL
Keep identity locked.
GEMINI / VEO HARD LOCK: Product-only commercial.

NEGATIVE PROMPT
- no generic chair
- no redesign

SAFETY AUDIT
Something secret

AIGC AUDIT
Do not paste this
""".trimIndent()

        val composed = GeminiVeoPromptCleanup.composeCopiedPrompt(
            rawPrompt = raw,
            voiceover = "Fester Rahmen, rotes Tablett.",
            title = "Fishing Chair Compact Fold",
            hashtags = listOf("#Chair", "#Fold"),
            marketplace = true,
            tiktokShopMode = true
        )
        val cleaned = GeminiVeoPromptCleanup.finalCleanupCopiedPrompt(composed, marketplace = true)

        assertFalse(cleaned.contains("SAFETY AUDIT"))
        assertFalse(cleaned.contains("AIGC AUDIT"))
        assertFalse(cleaned.contains("GEMINI AUDIT"))
        assertFalse(cleaned.contains("PRODUCT DESIGN = LOCKED"))
        assertFalse(cleaned.contains("CORE PRINCIPLE"))
        assertTrue(cleaned.contains("FORMAT"))
        assertTrue(cleaned.contains("PRODUCT LOCK"))
        assertTrue(cleaned.contains("TITLE"))
        assertTrue(cleaned.contains("HASHTAGS"))
        assertTrue(cleaned.contains("Fishing Chair Compact Fold"))
        assertTrue(cleaned.contains("#TikTokShop") || cleaned.contains("#Chair"))
        assertTrue(cleaned.contains("0.0"))
        assertTrue(cleaned.contains("8.0"))
        assertTrue(cleaned.contains("marketplace", ignoreCase = true))
        assertTrue(
            "too long: ${cleaned.length}",
            cleaned.length <= GeminiVeoPromptCleanup.MAX_COPIED_PROMPT_CHARS
        )
        val issues = GeminiVeoPromptCleanup.validateCompleteness(
            cleaned,
            listOf("#Chair", "#Fold", "#c", "#d", "#TikTokShop")
        )
        assertTrue(issues.none { it == "safety_audit_leaked" })
        assertTrue(issues.none { it == "section_order_wrong" })
    }

    @Test
    fun instructionalOnScreenTextBecomesNone() {
        assertEquals(
            "None.",
            GeminiVeoPromptCleanup.simplifyOnScreenText("Max 2–3 short overlays. No price or fake urgency.")
        )
        assertEquals(
            "None.",
            GeminiVeoPromptCleanup.simplifyOnScreenText("Do not repeat the whole voiceover.")
        )
    }

    @Test
    fun cleanupDoesNotRewriteStoredPathContract() {
        val stored = Fixtures.validVeoPrompt()
        val cleaned = ResultCompositionHelper.clean(stored)
        assertTrue(stored.contains("CORE PRINCIPLE") || stored.contains("same single physical product"))
        assertTrue(cleaned.contains("PRODUCT LOCK"))
        assertFalse(cleaned.contains("SAFETY AUDIT"))
        assertTrue(cleaned.length < stored.length || cleaned.contains("TITLE"))
    }

    private object ResultCompositionHelper {
        fun clean(stored: String): String {
            val composed = GeminiVeoPromptCleanup.composeCopiedPrompt(
                rawPrompt = stored,
                voiceover = "Tiefer Topf, fester Holzdeckel.",
                title = "Tiefe Pfanne",
                hashtags = listOf("#Pfanne", "#Kochen", "#Holzdeckel", "#Kitchen", "#TikTokShop"),
                marketplace = false,
                tiktokShopMode = true
            )
            return GeminiVeoPromptCleanup.finalCleanupCopiedPrompt(composed, marketplace = false)
        }
    }
}
