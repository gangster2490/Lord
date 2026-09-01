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
}
