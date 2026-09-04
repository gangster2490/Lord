package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalPromptValidatorTest {

    private val panModel = ProductModel(
        productCategory = "cookware",
        productIdentity = "Deep black pan with wooden lid",
        visualSignature = listOf(
            "deep rounded bowl",
            "high curved sides",
            "long dark wooden handle",
            "gold-tone ferrule",
            "two rivets",
            "hanging ring",
            "wooden crossbar lid"
        )
    )

    @Test
    fun hashtagNormalizationKeepsUnicodeLetters() {
        val tags = FinalPromptValidator.normalizeHashtags(
            listOf("#TiefePfanne", "#Holzdeckel", "#Kochen", "#Küche", "#TikTokShop"),
            tiktokShop = true
        )
        assertEquals(
            listOf("#TiefePfanne", "#Holzdeckel", "#Kochen", "#Küche", "#TikTokShop"),
            tags
        )
    }

    @Test
    fun validPackagePasses() {
        val response = StructuredResponse(
            veoPrompt = Fixtures.validVeoPrompt(panModel),
            voiceover = "Tiefer Topf, fester Holzdeckel, einfach kochen.",
            title = "Tiefe Pfanne mit Holzdeckel",
            hashtags = listOf("#Pfanne", "#Kochen", "#Holzdeckel", "#Kitchen", "#TikTokShop")
        )
        val report = FinalPromptValidator.validate(response, panModel, VoiceLanguage.DE, true)
        assertTrue(report.issues.joinToString(), report.ok)
        assertFalse(report.response.veoPrompt.contains("TITLE"))
        assertFalse(report.response.veoPrompt.contains("HASHTAGS"))
        assertEquals(5, report.response.hashtags.size)
    }

    @Test
    fun emptyPromptFails() {
        val report = FinalPromptValidator.validate(
            StructuredResponse(title = "x", hashtags = listOf("#a", "#b", "#c", "#d", "#e")),
            panModel,
            VoiceLanguage.OFF,
            false
        )
        assertFalse(report.ok)
        assertTrue(report.issues.any { it.field == "veoPrompt" })
    }

    @Test
    fun leakedTitleAndHashtagsAreSplitOut() {
        val prompt = Fixtures.validVeoPrompt(panModel) + "\n\nTITLE\nLeaked Title\n\nHASHTAGS\n#one #two #three #four #five"
        val split = FinalPromptValidator.splitLeakedMeta(
            StructuredResponse(veoPrompt = prompt, title = "", hashtags = emptyList())
        )
        assertEquals("Leaked Title", split.title)
        assertEquals(5, split.hashtags.size)
        assertFalse(split.veoPrompt.contains("\nTITLE\n") || split.veoPrompt.startsWith("TITLE"))
        assertFalse(Regex("(?im)^HASHTAGS\\s*$").containsMatchIn(split.veoPrompt))
    }

    @Test
    fun fourTimedBlocksRequired() {
        val broken = Fixtures.validVeoPrompt(panModel).replace("2.0–4.0s — IDENTITY", "IDENTITY")
        val report = FinalPromptValidator.validate(
            StructuredResponse(
                veoPrompt = broken,
                voiceover = "Ein Satz über die Pfanne mit Holzdeckel.",
                title = "Pfanne",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            panModel,
            VoiceLanguage.DE,
            true
        )
        assertTrue(report.issues.any { it.reason.startsWith("timed_blocks") })
    }

    @Test
    fun mechanicalEllipsisFails() {
        val prompt = Fixtures.validVeoPrompt(panModel).trimEnd() + "..."
        val report = FinalPromptValidator.validate(
            StructuredResponse(
                veoPrompt = prompt,
                voiceover = "Ein Satz über die Pfanne mit Holzdeckel.",
                title = "Pfanne",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            panModel,
            VoiceLanguage.DE,
            true
        )
        assertTrue(report.issues.any { it.reason.contains("truncated") })
    }

    @Test
    fun productLockMustBeSpecific() {
        val genericLock = Fixtures.validVeoPrompt(panModel).replace(
            Regex("(?is)PRODUCT LOCK\\n.*?\\n\\nSETTING"),
            "PRODUCT LOCK\nKeep the product the same.\n\nSETTING"
        )
        val report = FinalPromptValidator.validate(
            StructuredResponse(
                veoPrompt = genericLock,
                voiceover = "Ein Satz über die Pfanne mit Holzdeckel.",
                title = "Pfanne",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            panModel,
            VoiceLanguage.DE,
            true
        )
        assertTrue(report.issues.any { it.reason.contains("product_lock") || it.reason.contains("same_object") })
    }

    @Test
    fun localRepairAppendsMissingSignatureAndSameObjectWithoutTruncating() {
        val genericLock = Fixtures.validVeoPrompt(panModel).replace(
            Regex("(?is)PRODUCT LOCK\\n.*?\\n\\nSETTING"),
            "PRODUCT LOCK\nKeep the product the same.\n\nSETTING"
        )
        val originalLength = genericLock.length
        val repaired = FinalPromptValidator.localRepair(
            StructuredResponse(
                veoPrompt = genericLock,
                voiceover = "Ein Satz über die Pfanne mit Holzdeckel.",
                title = "Pfanne",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            panModel,
            VoiceLanguage.DE,
            true
        )
        assertTrue(repaired.veoPrompt.length >= originalLength)
        assertTrue(repaired.veoPrompt.contains("Keep the product the same."))
        assertTrue(repaired.veoPrompt.contains("deep rounded bowl"))
        assertTrue(repaired.veoPrompt.contains("same single physical product"))
        assertTrue(repaired.veoPrompt.contains("unchanged"))
        assertFalse(repaired.veoPrompt.endsWith("..."))
        val report = FinalPromptValidator.validate(repaired, panModel, VoiceLanguage.DE, true)
        assertTrue(report.issues.joinToString(), report.ok)
    }

    @Test
    fun localRepairAppendsMissingNegativeBulletsOnly() {
        val prompt = Fixtures.validVeoPrompt(panModel).replace(
            Regex("(?is)NEGATIVE PROMPT\\n.*"),
            "NEGATIVE PROMPT\n- no marketplace UI\n- no malformed hands"
        )
        val before = prompt
        val repaired = FinalPromptValidator.localRepair(
            StructuredResponse(
                veoPrompt = prompt,
                voiceover = "Ein Satz über die Pfanne mit Holzdeckel.",
                title = "Pfanne",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            panModel,
            VoiceLanguage.DE,
            true
        )
        assertTrue(repaired.veoPrompt.contains("- no marketplace UI"))
        assertTrue(repaired.veoPrompt.contains("- no malformed hands"))
        assertTrue(repaired.veoPrompt.contains("wooden crossbar lid") || repaired.veoPrompt.contains("wooden"))
        assertTrue(repaired.veoPrompt.contains("- no wok") || repaired.veoPrompt.contains("wok"))
        assertTrue(repaired.veoPrompt.length > before.length)
        val negative = FinalPromptValidator.sectionBody(repaired.veoPrompt, "NEGATIVE PROMPT")
        assertTrue(
            "product-specific bullets must come first so Gemini take(6) keeps them:\n$negative",
            negative.startsWith("- no generic replacement pan") ||
                negative.startsWith("- no missing") ||
                negative.startsWith("- no redesigned")
        )
        assertFalse(negative.startsWith("- no marketplace UI"))
    }

    @Test
    fun localRepairFlattensTwoLineShotsAndKeepsIdentityFirst() {
        val twoLine = Fixtures.validVeoPrompt(panModel).replace(
            Regex("(?is)SHOT SEQUENCE\\n.*?\\n\\nON-SCREEN TEXT"),
            """
SHOT SEQUENCE
0.0–2.0s — HOOK
Product visible immediately with strongest verified visual detail: deep rounded bowl, wooden crossbar lid.
2.0–4.0s — IDENTITY
Clear framing of the same exact physical product.
4.0–6.0s — FEATURE / DEMO
Exactly one verified hero feature or physically plausible action with one hand.
6.0–8.0s — HERO / CTA
Stable desirable hero shot of the same unchanged product. End exactly at 8.0s.

ON-SCREEN TEXT
""".trimIndent()
        )
        val repaired = FinalPromptValidator.localRepair(
            StructuredResponse(
                veoPrompt = twoLine,
                voiceover = "Ein Satz über die Pfanne mit Holzdeckel.",
                title = "Pfanne",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            panModel,
            VoiceLanguage.DE,
            true
        )
        val shots = FinalPromptValidator.sectionBody(repaired.veoPrompt, "SHOT SEQUENCE")
        val hook = shots.lineSequence().first { it.contains("0.0") }
        assertTrue(hook.contains("0.0–2.0s — HOOK:"))
        val afterLabel = hook.substringAfter("HOOK:").trim()
        assertTrue(
            "identity must lead the timed line:\n$hook",
            afterLabel.startsWith("deep rounded bowl") || afterLabel.contains("wooden crossbar lid")
        )
        assertEquals(4, shots.lineSequence().count { it.isNotBlank() })
    }

    @Test
    fun localRepairStripsLeakedPanOverlaysForChair() {
        val chair = Fixtures.fishingChairModel()
        val leaked = Fixtures.validVeoPrompt(chair).replace(
            "Rahmen\nTablett",
            "Holzdeckel\nTiefe Form"
        )
        val repaired = FinalPromptValidator.localRepair(
            StructuredResponse(
                veoPrompt = leaked,
                voiceover = "Tiefer Topf, fester Holzdeckel, einfach kochen.",
                title = "Chair",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            chair,
            VoiceLanguage.DE,
            true
        )
        val overlays = FinalPromptValidator.sectionBody(repaired.veoPrompt, "ON-SCREEN TEXT")
        assertFalse(overlays.contains("Holzdeckel"))
        assertFalse(overlays.contains("Tiefe Form"))
        assertTrue(overlays.contains("Rahmen") || overlays.contains("Tablett"))
        assertFalse(repaired.voiceover.contains("Holzdeckel", ignoreCase = true))
        assertFalse(
            FinalPromptValidator.sectionBody(repaired.veoPrompt, "SETTING")
                .contains("premium kitchen", ignoreCase = true)
        )
    }
}
