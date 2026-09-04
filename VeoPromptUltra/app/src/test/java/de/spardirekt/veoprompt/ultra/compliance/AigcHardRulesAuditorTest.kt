package de.spardirekt.veoprompt.ultra.compliance

import de.spardirekt.veoprompt.ultra.generation.Fixtures
import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AigcHardRulesAuditorTest {

    private val pan = ProductModel(
        productIdentity = "Deep black pan with wooden lid",
        visualSignature = listOf("deep rounded bowl", "wooden handle", "wooden lid")
    )

    @Test
    fun catalogHasHardCodes() {
        val codes = AigcHardRules.RULES.map { it.code }.toSet()
        assertTrue(
            codes.containsAll(
                listOf(
                    "AIGC_DISCLOSE",
                    "AIGC_NO_DECEIVE",
                    "AIGC_NO_IMPERSONATE",
                    "AIGC_NO_FALSE_ENDORSE",
                    "AIGC_NO_PRODUCT_ALTER",
                    "AIGC_NO_UNREALISTIC",
                    "AIGC_NO_FAKE_FEATURES",
                    "AIGC_NO_FEAR",
                    "AIGC_NO_IP",
                    "AIGC_PRODUCT_MATCH"
                )
            )
        )
        assertEquals("2026.05-v1", AigcHardRules.VERSION)
    }

    @Test
    fun discloseIsInfoAndDoesNotRaiseRisk() {
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
        assertEquals("LOW", result.audit.riskLevel)
        assertTrue(result.audit.items.any { it.contains("AIGC_DISCLOSE") && it.contains("INFO") })
    }

    @Test
    fun fakeDoctorIsHighRiskAndSanitized() {
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
        assertEquals("HIGH", result.audit.riskLevel)
        assertTrue(result.audit.items.any { it.contains("AIGC_NO_IMPERSONATE") || it.contains("AIGC_NO_FALSE_ENDORSE") })
        assertFalse(result.response.voiceover.contains("as a doctor", ignoreCase = true))
        assertFalse(result.response.veoPrompt.contains("SAFETY AUDIT"))
    }

    @Test
    fun instantHairMiracleIsHighRisk() {
        val result = TikTokShopComplianceAuditor.audit(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(pan),
                voiceover = "This shampoo instantly grows hair and fills bald spots.",
                title = "Miracle",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE,
            tiktokShopMode = false
        )
        assertEquals("HIGH", result.audit.riskLevel)
        assertTrue(result.audit.items.any { it.contains("AIGC_NO_UNREALISTIC") })
    }

    @Test
    fun productAlterAndFake3dAreFlagged() {
        val prompt = Fixtures.validVeoPrompt(pan) + "\nchange the product color and turn the product into 3d"
        val result = AigcHardRulesAuditor.audit(
            prompt = prompt,
            voiceover = "Simple kitchen line.",
            title = "Pan",
            overlay = "None",
            productModel = pan,
            tiktokShopMode = true
        )
        assertTrue(result.findings.any { it.code == "AIGC_NO_PRODUCT_ALTER" })
        assertTrue(result.findings.any { it.code == "AIGC_NO_FAKE_FEATURES" })
        assertTrue(result.findings.any { it.code == "AIGC_DISCLOSE" && it.severity == "INFO" })
    }

    @Test
    fun enrichAppendsWithoutTruncating() {
        val original = Fixtures.validVeoPrompt(pan)
        val enriched = AigcHardRulesAuditor.enrichPrompt(original)
        assertTrue(enriched.length >= original.length)
        assertTrue(enriched.contains("The same single physical product"))
        assertTrue(enriched.contains("AIGC HARD LOCK"))
        assertTrue(enriched.contains("no fake doctor"))
        assertFalse(enriched.endsWith("..."))
        assertFalse(enriched.contains("AIGC AUDIT"))
        val again = AigcHardRulesAuditor.enrichPrompt(enriched)
        assertEquals(enriched, again)
    }

    @Test
    fun fearVisualsAreHighRisk() {
        val result = AigcHardRulesAuditor.audit(
            prompt = Fixtures.validVeoPrompt(pan),
            voiceover = "Look at these damaged organs if you skip the pan.",
            title = "Pan",
            overlay = "None",
            productModel = pan,
            tiktokShopMode = true
        )
        assertTrue(result.findings.any { it.code == "AIGC_NO_FEAR" && it.severity == "HIGH" })
    }
}
