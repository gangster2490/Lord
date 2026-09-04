package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.ProductModel
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

    @Test
    fun twoLineChairKeepsIdentityAndDropsPanLeaks() {
        val details = "metal frame, padded backrest, side tray, rubber feet"
        val raw = """
FORMAT
Vertical 9:16. Exactly 8.0 seconds.

REFERENCES
Recreate only the physical product.

PRODUCT LOCK
Preserve $details.

SETTING
Product-appropriate premium kitchen.

SHOT SEQUENCE
0.0–2.0s — HOOK
Product visible immediately with strongest verified visual detail: $details.
2.0–4.0s — IDENTITY
Clear framing of the same exact physical product.
4.0–6.0s — FEATURE / DEMO
Exactly one verified hero feature or physically plausible action with one hand.
6.0–8.0s — HERO / CTA
Stable desirable hero shot of the same unchanged product. End exactly at 8.0s.

ON-SCREEN TEXT
Holzdeckel
Tiefe Form

VOICEOVER
Tiefer Topf, fester Holzdeckel, einfach kochen.

AUDIO
Subtle music.

CRITICAL
Keep identity locked.

NEGATIVE PROMPT
- no generic replacement pan or wok
- no redesigned silhouette or shallower bowl
- no missing wooden lid, ferrule, rivets or hanging ring
- no changed handle geometry
- no invented non-stick claims
- no product morphing between shots
- no marketplace UI
- no missing metal frame
- no missing padded backrest
- no missing side tray
""".trimIndent()
        val cleaned = GeminiVeoPromptCleanup.composeCopiedPrompt(
            rawPrompt = raw,
            voiceover = "Fester Rahmen, gepolsterte Lehne.",
            title = "folding fishing chair",
            hashtags = listOf("#Chair", "#Fold", "#c", "#d", "#TikTokShop"),
            marketplace = true,
            tiktokShopMode = true
        )
        val shots = section(cleaned, "SHOT SEQUENCE")
        val hook = shots.lineSequence().first { it.contains("0.0") }
        assertTrue("hook must keep identity:\n$hook", hook.contains("frame") || hook.contains("tray"))
        assertTrue(hook.contains(":"))
        val negatives = section(cleaned, "NEGATIVE PROMPT")
        assertTrue(negatives.contains("frame") || negatives.contains("tray") || negatives.contains("backrest"))
        assertFalse(negatives.contains("wok"))
        assertFalse(negatives.contains("shallower bowl"))
        assertFalse(negatives.contains("non-stick"))
        assertFalse(negatives.contains("ferrule"))
        val overlays = section(cleaned, "ON-SCREEN TEXT")
        assertFalse(overlays.contains("Holzdeckel"))
        assertFalse(overlays.contains("Tiefe Form"))
        val setting = section(cleaned, "SETTING")
        assertFalse(setting.equals("kitchen.", ignoreCase = true))
        assertFalse(setting.contains("premium kitchen", ignoreCase = true))
        val lock = section(cleaned, "PRODUCT LOCK")
        assertTrue(lock.contains("metal frame"))
        assertTrue(cleaned.length <= GeminiVeoPromptCleanup.MAX_COPIED_PROMPT_CHARS)
    }

    @Test
    fun panCopyKeepsWoodenLid() {
        val stored = Fixtures.validVeoPrompt(
            ProductModel(
                productIdentity = "Deep black pan with wooden lid",
                visualSignature = listOf(
                    "deep rounded bowl", "high sides", "wooden handle",
                    "ferrule", "rivets", "hanging ring", "wooden lid"
                )
            )
        )
        val cleaned = ResultCompositionHelper.clean(stored)
        val lock = section(cleaned, "PRODUCT LOCK")
        assertTrue(
            "wooden lid must survive compress:\n$lock",
            lock.contains("wooden lid")
        )
        assertTrue("ferrule must survive compress:\n$lock", lock.contains("ferrule"))
        val overlays = section(cleaned, "ON-SCREEN TEXT")
        assertTrue(overlays.contains("Holzdeckel"))
        val hook = section(cleaned, "SHOT SEQUENCE").lineSequence().first { it.contains("0.0") }
        assertTrue(
            "HOOK must keep wooden lid and ferrule:\n$hook",
            hook.contains("wooden lid") && hook.contains("ferrule")
        )
        assertTrue("HOOK must be a filmable close-up:\n$hook", hook.contains("close-up"))
        assertTrue("HOOK must specify composition:\n$hook", hook.contains("frames"))
        val feature = section(cleaned, "SHOT SEQUENCE").lineSequence().first { it.contains("FEATURE") }
        assertTrue("FEATURE must be a concrete safe camera move:\n$feature", feature.contains("push-in"))
        assertTrue("FEATURE must keep the product still:\n$feature", feature.contains("remains still"))
        assertFalse("FEATURE placeholder leaked:\n$feature", feature.contains("verified action"))
        assertTrue(cleaned.contains("listing UI") || cleaned.contains("define appearance"))
        assertFalse(cleaned.contains("no…"))
    }

    private fun section(prompt: String, header: String): String {
        val pattern = Regex("(?im)^" + Regex.escape(header) + "\\s*$")
        val match = pattern.find(prompt) ?: return ""
        val start = match.range.last + 1
        val next = Regex(
            "(?im)^(FORMAT|REFERENCES|PRODUCT LOCK|SETTING|SHOT SEQUENCE|ON-SCREEN TEXT|VOICEOVER|AUDIO|CRITICAL|NEGATIVE PROMPT|TITLE|HASHTAGS)\\s*$"
        ).find(prompt, start)
        return prompt.substring(start, next?.range?.first ?: prompt.length).trim()
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
