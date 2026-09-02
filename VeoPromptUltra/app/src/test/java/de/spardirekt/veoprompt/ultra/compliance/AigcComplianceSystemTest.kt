package de.spardirekt.veoprompt.ultra.compliance

import de.spardirekt.veoprompt.ultra.generation.Fixtures
import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AigcComplianceSystemTest {

    private val pan = ProductModel(
        productIdentity = "Deep black pan with wooden lid",
        visualSignature = listOf("deep rounded bowl", "wooden handle", "wooden lid")
    )

    @Test
    fun cleanPackageIsDiscloseRequiredAndPublishSafe() {
        val result = TikTokShopComplianceAuditor.audit(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(pan),
                voiceover = "Tiefer Topf mit festem Holzdeckel hält die Hitze.",
                title = "Tiefe Pfanne mit Holzdeckel",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            pan,
            VoiceLanguage.DE,
            tiktokShopMode = true
        )
        assertEquals(AigcComplianceSystem.VERDICT_DISCLOSE, result.audit.aigc.verdict)
        assertTrue(result.audit.aigc.disclosureRequired)
        assertTrue(result.audit.aigc.shopPublishSafe)
        assertTrue(result.audit.aigc.checklist.any { it.startsWith("OK · AIGC_DISCLOSE") })
        assertTrue(result.audit.aigc.checklist.all { it.startsWith("OK") })
        assertTrue(result.audit.aigc.publishSteps.any { it.contains("AI-generated content") })
        assertFalse(result.response.veoPrompt.contains("AIGC AUDIT"))
        assertFalse(result.response.veoPrompt.contains("SAFETY AUDIT"))
    }

    @Test
    fun impersonationBlocksShopPublish() {
        val result = TikTokShopComplianceAuditor.audit(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(pan),
                voiceover = "As a doctor I recommend this pan every morning.",
                title = "Pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE,
            tiktokShopMode = true
        )
        assertEquals(AigcComplianceSystem.VERDICT_BLOCKED, result.audit.aigc.verdict)
        assertFalse(result.audit.aigc.shopPublishSafe)
        assertTrue(result.audit.aigc.checklist.any { it.startsWith("FAIL · AIGC_NO_IMPERSONATE") })
        assertTrue(result.audit.aigc.publishSteps.any { it.contains("Не публикуйте") })
        assertFalse(result.response.veoPrompt.contains("SAFETY AUDIT"))
    }

    @Test
    fun claimingRealFootageIsDeception() {
        val result = TikTokShopComplianceAuditor.audit(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(pan),
                voiceover = "This is real footage, not AI generated.",
                title = "Pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE,
            tiktokShopMode = false
        )
        assertEquals("HIGH", result.audit.riskLevel)
        assertEquals(AigcComplianceSystem.VERDICT_BLOCKED, result.audit.aigc.verdict)
        assertTrue(result.audit.items.any { it.contains("AIGC_NO_DECEIVE") })
        assertTrue(result.audit.items.any { it.contains("AIGC_DISCLOSE") })
    }

    @Test
    fun discloseAppliesEvenWhenShopModeOff() {
        val scanned = AigcHardRulesAuditor.audit(
            prompt = Fixtures.validVeoPrompt(pan),
            voiceover = "Simple kitchen line.",
            title = "Pan",
            overlay = "None",
            productModel = pan,
            tiktokShopMode = false
        )
        assertTrue(scanned.findings.any { it.code == "AIGC_DISCLOSE" })
    }
}
