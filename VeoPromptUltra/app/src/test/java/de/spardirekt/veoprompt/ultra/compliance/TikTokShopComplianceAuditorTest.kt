package de.spardirekt.veoprompt.ultra.compliance

import de.spardirekt.veoprompt.ultra.generation.Fixtures
import de.spardirekt.veoprompt.ultra.model.ProductModel
import de.spardirekt.veoprompt.ultra.model.StructuredResponse
import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokShopComplianceAuditorTest {

    private val pan = ProductModel(
        productIdentity = "Deep black pan with wooden lid",
        visualSignature = listOf("deep rounded bowl", "wooden handle", "wooden lid")
    )

    @Test
    fun cleanPackageIsLowRiskAndKeepsPrompt() {
        val prompt = Fixtures.validVeoPrompt(pan)
        val result = TikTokShopComplianceAuditor.audit(
            StructuredResponse(
                veoPrompt = prompt,
                voiceover = "Tiefer Topf, fester Holzdeckel, einfach kochen.",
                title = "Tiefe Pfanne mit Holzdeckel",
                hashtags = listOf("#Pfanne", "#Kochen", "#Holzdeckel", "#Kitchen", "#TikTokShop")
            ),
            pan,
            VoiceLanguage.DE,
            tiktokShopMode = true
        )
        assertEquals("LOW", result.audit.riskLevel)
        assertEquals(prompt, result.response.veoPrompt)
        assertFalse(result.response.veoPrompt.contains("SAFETY AUDIT"))
        assertTrue(result.audit.policyVersion == TikTokShopPolicy.VERSION)
        assertTrue(result.audit.items.any { it.contains("AI_LABEL") })
    }

    @Test
    fun superlativeAndPriceAreFlaggedAndSanitized() {
        val prompt = Fixtures.validVeoPrompt(pan).replace(
            "Holzdeckel\nTiefe Form",
            "50% OFF\nJetzt kaufen"
        )
        val result = TikTokShopComplianceAuditor.audit(
            StructuredResponse(
                veoPrompt = prompt,
                voiceover = "The cheapest pan guaranteed. Last chance!",
                title = "Best ever pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop")
            ),
            pan,
            VoiceLanguage.DE,
            tiktokShopMode = true
        )
        assertTrue(result.audit.items.any { it.contains("CL_SUPERLATIVE") })
        assertTrue(result.audit.items.any { it.contains("PR_PRICE_UI") || it.contains("PR_URGENCY") })
        assertFalse(result.response.voiceover.contains("cheapest", ignoreCase = true))
        assertFalse(result.response.voiceover.contains("guaranteed", ignoreCase = true))
        assertFalse(result.response.veoPrompt.contains("50% OFF"))
        assertFalse(result.response.veoPrompt.contains("SAFETY AUDIT"))
        assertEquals(prompt.length > 100, result.response.veoPrompt.length > 100)
    }

    @Test
    fun medicalClaimIsHighRisk() {
        val result = TikTokShopComplianceAuditor.audit(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(pan),
                voiceover = "This pan clinically proven cures inflammation.",
                title = "Miracle pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE,
            tiktokShopMode = false
        )
        assertEquals("HIGH", result.audit.riskLevel)
        assertTrue(result.audit.items.any { it.contains("CL_MEDICAL") })
    }

    @Test
    fun unverifiedWaterproofIsUnsupported() {
        val result = TikTokShopComplianceAuditor.audit(
            StructuredResponse(
                veoPrompt = Fixtures.validVeoPrompt(pan),
                voiceover = "Completely waterproof wooden lid.",
                title = "Pan",
                hashtags = listOf("#a", "#b", "#c", "#d", "#e")
            ),
            pan,
            VoiceLanguage.DE,
            tiktokShopMode = false
        )
        assertTrue(result.audit.items.any { it.contains("CL_UNSUPPORTED") })
    }

    @Test
    fun policyCatalogHasRequiredCodes() {
        val codes = TikTokShopPolicy.RULES.map { it.code }.toSet()
        assertTrue(codes.containsAll(listOf(
            "CQ_ACCURACY", "CL_UNSUPPORTED", "CL_MEDICAL", "CL_SUPERLATIVE",
            "PR_PRICE_UI", "PR_URGENCY", "PR_SYMPATHY", "PR_OFF_PLATFORM", "AI_LABEL"
        )))
        assertEquals("TikTok Shop Content Quality & Compliance Policy", TikTokShopPolicy.TITLE)
    }
}
