package de.spardirekt.ugcagent.v3.compliance

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplianceEngineTest {
    @Test
    fun passOnCleanEvidenceBasedCopy() {
        val analysis = JSONObject().put("text_claims", org.json.JSONArray().put("spülmaschinengeeignet laut Produktkarte"))
        val result = ComplianceEngine.review(
            prompt = "Use the uploaded reference image as the strict visual identity reference. A person uses the referenced product in a normal kitchen. No spoken dialogue.",
            speech = "No spoken dialogue.",
            caption = "Alltag in der Küche.\nWerbung",
            hashtags = listOf("#tiktokshop", "#küche"),
            analysis = analysis,
            semantic = null,
        )
        assertEquals("PASS", result.getString("status"))
    }

    @Test
    fun warnsOnAbsoluteClaim() {
        val result = ComplianceEngine.review("Das ist das beste Produkt", "", "Caption", emptyList(), null, null)
        assertEquals("WARNING", result.getString("status"))
        assertTrue(result.getJSONArray("unsupported_claims").toString().contains("beste"))
    }

    @Test
    fun blocksMedicalClaim() {
        val result = ComplianceEngine.review("Dieses Mittel heilt alles", "", "", emptyList(), null, null)
        assertEquals("BLOCK", result.getString("status"))
    }

    @Test
    fun warnsWhenWerbungMissing() {
        val result = ComplianceEngine.review("prompt", "", "Tolle Küche heute", emptyList(), null, null)
        assertTrue(result.getJSONArray("warnings").toString().contains("Werbung"))
    }

    @Test
    fun addWerbungDoesNotDuplicate() {
        assertEquals("Hallo\nWerbung", ComplianceEngine.addWerbung("Hallo"))
        assertEquals("Werbung schon da", ComplianceEngine.addWerbung("Werbung schon da"))
    }

    @Test
    fun ignoresMarketplaceUiInCopy() {
        val result = ComplianceEngine.review("Earn €1.44 per sale with 8% commission", "", "Werbung", emptyList(), null, null)
        assertTrue(result.getJSONArray("unsupported_claims").toString().contains("marketplace UI"))
    }

    @Test
    fun marketplaceFilterDetectsCommission() {
        assertTrue(MarketplaceFilter.containsMarketplaceUi("8% commission"))
        assertFalse(MarketplaceFilter.containsMarketplaceUi("the referenced product in a kitchen"))
    }
}
