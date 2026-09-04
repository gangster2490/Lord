package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import de.spardirekt.veoprompt.ultra.ui.result.ResultComposition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gemini copy must keep photographed identity even when the stored prompt is
 * two-line shots with pan-default overlays and pan-first negatives.
 */
class CleanupIdentityRegressionTest {

    @Test
    fun panGeminiCopyKeepsLidFerruleAndPanOverlays() {
        val model = Fixtures.panModel()
        val repaired = FinalPromptValidator.localRepair(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(model),
                voiceover = "Tiefer Topf, fester Holzdeckel, einfach kochen.",
                title = "Tiefe Pfanne mit Holzdeckel",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            model,
            VoiceLanguage.DE,
            true
        )
        val copy = ResultComposition.geminiPrompt(
            storedPrompt = repaired.veoPrompt,
            voiceover = repaired.voiceover,
            title = repaired.title,
            hashtags = repaired.hashtags,
            marketplace = true,
            tiktokShopMode = true
        )
        assertPanCopy(copy)
    }

    @Test
    fun twoLinePanSurvivesCleanupWithoutLocalRepair() {
        val model = Fixtures.panModel()
        val copy = ResultComposition.geminiPrompt(
            storedPrompt = twoLinePanShapedPrompt(model),
            voiceover = "Tiefer Topf, fester Holzdeckel, einfach kochen.",
            title = model.productIdentity,
            hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop"),
            marketplace = true,
            tiktokShopMode = true
        )
        assertPanCopy(copy)
        val hook = section(copy, "SHOT SEQUENCE").lineSequence().first { it.contains("0.0") }
        assertTrue(
            "hook must keep pan identity:\n$hook",
            hook.contains("lid") || hook.contains("ferrule") || hook.contains("bowl")
        )
    }

    private fun assertPanCopy(copy: String) {
        assertTrue(
            "too long: ${copy.length}\n$copy",
            copy.length <= GeminiVeoPromptCleanup.MAX_COPIED_PROMPT_CHARS
        )
        val identity = section(copy, "PRODUCT LOCK") + "\n" + section(copy, "SHOT SEQUENCE")
        assertTrue("missing wooden lid:\n$identity", identity.contains("wooden lid"))
        assertTrue("missing ferrule:\n$identity", identity.contains("ferrule"))
        assertTrue("missing bowl:\n$identity", identity.contains("bowl"))
        val overlays = section(copy, "ON-SCREEN TEXT")
        assertTrue("missing Holzdeckel:\n$overlays", overlays.contains("Holzdeckel"))
        val negatives = section(copy, "NEGATIVE PROMPT")
        assertTrue(
            "pan negatives lost wok/lid/ferrule:\n$negatives",
            negatives.contains("wok") ||
                negatives.contains("wooden lid") ||
                negatives.contains("ferrule")
        )
        val hook = section(copy, "SHOT SEQUENCE").lineSequence().first { it.contains("0.0") }
        assertTrue(hook.contains(":"))
        assertTrue(hook.length > "0.0–2.0s — HOOK".length)
    }

    @Test
    fun chairGeminiCopyKeepsChairIdentityAndDropsPanLeaks() {
        assertCopyKeepsProduct(
            model = Fixtures.fishingChairModel(),
            mustKeep = listOf("frame", "backrest", "tray"),
            mustDrop = listOf("Holzdeckel", "Tiefe Form", "non-stick", "ferrule", "shallower bowl")
        )
    }

    @Test
    fun bitsGeminiCopyKeepsBitIdentityAndDropsPanLeaks() {
        assertCopyKeepsProduct(
            model = Fixtures.phBitsModel(),
            mustKeep = listOf("PH", "collar"),
            mustDrop = listOf("Holzdeckel", "Tiefe Form", "shallower bowl", "non-stick")
        )
    }

    @Test
    fun riceStoveGrillCopiesStayOnTheirOwnProduct() {
        assertCopyKeepsProduct(
            model = Fixtures.riceWasherModel(),
            mustKeep = listOf("bowl", "drain"),
            mustDrop = listOf("Holzdeckel", "Tiefe Form", "shallower bowl")
        )
        assertCopyKeepsProduct(
            model = Fixtures.stoveCaseModel(),
            mustKeep = listOf("closed", "case"),
            mustDrop = listOf("Holzdeckel", "Tiefe Form", "shallower bowl")
        )
        assertCopyKeepsProduct(
            model = Fixtures.contactGrillModel(),
            mustKeep = listOf("plate"),
            mustDrop = listOf("Holzdeckel", "Tiefe Form", "shallower bowl")
        )
    }

    @Test
    fun twoLineChairSurvivesCleanupWithoutLocalRepair() {
        val model = Fixtures.fishingChairModel()
        val copy = ResultComposition.geminiPrompt(
            storedPrompt = twoLinePanShapedPrompt(model),
            voiceover = "Fester Rahmen, gepolsterte Lehne.",
            title = model.productIdentity,
            hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop"),
            marketplace = true,
            tiktokShopMode = true
        )
        val lockOrShots = section(copy, "PRODUCT LOCK") + "\n" + section(copy, "SHOT SEQUENCE")
        assertTrue(lockOrShots.contains("frame") || lockOrShots.contains("tray"))
        assertFalse(copy.contains("Holzdeckel"))
        assertFalse(copy.contains("Tiefe Form"))
        val negatives = section(copy, "NEGATIVE PROMPT")
        assertFalse(negatives.contains("wok"))
        assertFalse(negatives.contains("shallower bowl"))
        assertFalse(negatives.contains("non-stick"))
        assertTrue(
            negatives.contains("frame") ||
                negatives.contains("tray") ||
                negatives.contains("backrest")
        )
        val hook = section(copy, "SHOT SEQUENCE").lineSequence().first { it.contains("0.0") }
        assertTrue(hook.contains("frame") || hook.contains("backrest") || hook.contains("tray"))
    }

    @Test
    fun twoLineLegacyChairSurvivesCleanupAfterLocalRepair() {
        val model = Fixtures.fishingChairModel()
        val legacy = twoLinePanShapedPrompt(model)
        val repaired = FinalPromptValidator.localRepair(
            StructuredResponse(
                veoPrompt = legacy,
                voiceover = "Tiefer Topf, fester Holzdeckel, einfach kochen.",
                title = model.productIdentity,
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            model,
            VoiceLanguage.DE,
            true
        )
        val copy = ResultComposition.geminiPrompt(
            storedPrompt = repaired.veoPrompt,
            voiceover = repaired.voiceover,
            title = repaired.title,
            hashtags = repaired.hashtags,
            marketplace = true,
            tiktokShopMode = true
        )
        val lockOrShots = section(copy, "PRODUCT LOCK") + "\n" + section(copy, "SHOT SEQUENCE")
        assertTrue(lockOrShots.contains("frame") || lockOrShots.contains("tray"))
        assertFalse(copy.contains("Holzdeckel"))
        assertFalse(copy.contains("Tiefe Form"))
        val negatives = section(copy, "NEGATIVE PROMPT")
        assertFalse(negatives.contains("shallower bowl"))
        assertFalse(negatives.contains("non-stick"))
        assertTrue(
            negatives.contains("frame") ||
                negatives.contains("tray") ||
                negatives.contains("camping chair") ||
                negatives.contains("backrest")
        )
        val hook = section(copy, "SHOT SEQUENCE").lineSequence().first { it.contains("0.0") }
        assertTrue(hook.length > "0.0–2.0s — HOOK".length)
        assertTrue(hook.contains("frame") || hook.contains("backrest") || hook.contains("tray"))
    }

    private fun assertCopyKeepsProduct(
        model: ProductModel,
        mustKeep: List<String>,
        mustDrop: List<String>
    ) {
        val repaired = FinalPromptValidator.localRepair(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(model),
                voiceover = "placeholder",
                title = model.productIdentity,
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            model,
            VoiceLanguage.DE,
            true
        )
        val copy = ResultComposition.geminiPrompt(
            storedPrompt = repaired.veoPrompt,
            voiceover = repaired.voiceover,
            title = repaired.title,
            hashtags = repaired.hashtags,
            marketplace = true,
            tiktokShopMode = true
        )
        val identityBlob = section(copy, "PRODUCT LOCK") + "\n" + section(copy, "SHOT SEQUENCE")
        mustKeep.forEach { token ->
            assertTrue(
                "expected '$token' in chair/bits identity for ${model.productIdentity}:\n$identityBlob",
                identityBlob.contains(token, ignoreCase = true)
            )
        }
        mustDrop.forEach { token ->
            assertFalse(
                "leaked '$token' into Gemini copy for ${model.productIdentity}:\n$copy",
                copy.contains(token, ignoreCase = true)
            )
        }
        val hook = section(copy, "SHOT SEQUENCE").lineSequence().first { it.contains("0.0") }
        assertTrue(hook.contains(":"))
        assertTrue(hook.length > "0.0–2.0s — HOOK".length)
    }

    private fun twoLinePanShapedPrompt(model: ProductModel): String {
        val details = model.visualSignature.joinToString(", ")
        return """
FORMAT
Vertical 9:16.
Photorealistic commercial TikTok Shop product ad style.
Generate exactly 8.0 seconds total.
Timeline ends at exactly 8.0s.
Use exactly four 2.0-second blocks.

REFERENCES
Recreate only the physical product.

PRODUCT LOCK
The same single physical product shown in the uploaded photos must remain unchanged across all four shots.
Preserve $details.

SETTING
Product-appropriate premium kitchen. Simple background. Realistic lighting. Product dominant.

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
Subtle background music. Clear voice.

CRITICAL
Keep the same product.

NEGATIVE PROMPT
- no generic replacement pan or wok
- no redesigned silhouette or shallower bowl
- no missing wooden lid, ferrule, rivets or hanging ring
- no changed handle geometry
- no invented non-stick claims
- no product morphing between shots
- no marketplace UI
""".trimIndent()
    }

    private fun section(prompt: String, header: String): String {
        val pattern = Regex("(?im)^" + Regex.escape(header) + "\\s*$")
        val match = pattern.find(prompt) ?: return ""
        val start = match.range.last + 1
        val next = Regex("(?im)^(FORMAT|REFERENCES|PRODUCT LOCK|SETTING|SHOT SEQUENCE|ON-SCREEN TEXT|VOICEOVER|AUDIO|CRITICAL|NEGATIVE PROMPT|TITLE|HASHTAGS)\\s*$")
            .find(prompt, start)
        return prompt.substring(start, next?.range?.first ?: prompt.length).trim()
    }
}
