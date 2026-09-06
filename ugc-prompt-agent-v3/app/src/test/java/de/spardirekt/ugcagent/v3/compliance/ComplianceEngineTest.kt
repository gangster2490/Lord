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
    fun absoluteClaimDoesNotSurfaceWarningAfterFix() {
        val result = ComplianceEngine.review("Das ist das beste Produkt", "", "Caption\nWerbung", emptyList(), null, null)
        assertTrue(result.getJSONArray("unsupported_claims").toString().contains("beste"))
        assertEquals("PASS", result.getString("status"))
        val fixed = ComplianceEngine.enforceAndFix(
            prompt = "Das ist das beste Produkt. Keep the referenced product unchanged.",
            caption = "Das ist das beste Produkt, bewahrt Feuchtigkeit, BPA-free.",
            hashtags = listOf("#tiktokshop", "#küche", "#alltag", "#ugc"),
            analysis = JSONObject().put("product_category", "kitchen").put("observed_use_case", "cover food"),
            evidence = null,
            fingerprint = de.spardirekt.ugcagent.v3.prompt.ProductIdentity.microwaveCoverFingerprint(),
            language = "DEUTSCH",
        )
        assertEquals("PASS", fixed.review.getString("status"))
        assertEquals(0, fixed.review.getJSONArray("blocked_reasons").length())
        assertFalse(fixed.caption.contains("Feuchtigkeit"))
        assertFalse(fixed.caption.contains("beste"))
        assertTrue(fixed.caption.contains("Werbung"))
    }

    @Test
    fun blocksMedicalClaim() {
        val result = ComplianceEngine.review("Dieses Mittel heilt alles", "", "", emptyList(), null, null)
        assertEquals("BLOCK", result.getString("status"))
    }

    @Test
    fun missingDisclosureIsNoteNotWarning() {
        val result = ComplianceEngine.review("prompt", "", "Tolle Küche heute", emptyList(), null, null)
        assertEquals("PASS", result.getString("status"))
        assertTrue(result.getJSONArray("notes").toString().contains("Werbung"))
        assertEquals(0, result.getJSONArray("warnings").length())
    }

    @Test
    fun addWerbungDoesNotDuplicate() {
        assertEquals("Hallo\nWerbung", ComplianceEngine.addWerbung("Hallo"))
        assertEquals("Werbung schon da", ComplianceEngine.addWerbung("Werbung schon da"))
        assertEquals("Текст\nAnzeige", ComplianceEngine.addDisclosure("Текст", "РУССКИЙ"))
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
