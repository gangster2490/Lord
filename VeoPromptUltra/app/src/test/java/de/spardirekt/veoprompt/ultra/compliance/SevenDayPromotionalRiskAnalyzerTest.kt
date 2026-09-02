package de.spardirekt.veoprompt.ultra.compliance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SevenDayPromotionalRiskAnalyzerTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun emptyWindowIsLow() {
        val report = SevenDayPromotionalRiskAnalyzer.analyze(emptyList(), now)
        assertEquals("LOW", report.riskLevel)
        assertEquals(0, report.score)
        assertEquals(0, report.projectedRestrictionDays)
        assertEquals(7, report.windowDays)
        assertEquals("2026.06-v1", report.policyVersion)
    }

    @Test
    fun ignoresDraftsAndItemsOlderThanSevenDays() {
        val snaps = listOf(
            snap("old", now - 8 * day, "Ready", items = listOf("PR_URGENCY · HIGH · last chance")),
            snap("draft", now - hour(), "Draft", hasPackage = false, items = listOf("PR_URGENCY · HIGH · hurry"))
        )
        val report = SevenDayPromotionalRiskAnalyzer.analyze(snaps, now)
        assertEquals(0, report.analyzedCount)
        assertEquals("LOW", report.riskLevel)
    }

    @Test
    fun singleHighPromoIsMediumWithThreeDayWarning() {
        val snaps = listOf(
            snap("p1", now - day, "Ready", items = listOf("PR_URGENCY · HIGH · last chance"))
        )
        val report = SevenDayPromotionalRiskAnalyzer.analyze(snaps, now)
        assertEquals(1, report.analyzedCount)
        assertEquals(1, report.highHits)
        assertEquals("MEDIUM", report.riskLevel)
        assertEquals(3, report.projectedRestrictionDays)
        assertTrue(report.score in 12..19)
    }

    @Test
    fun repeatCodeInSevenDaysIsHighWithSevenDayWarning() {
        val snaps = listOf(
            snap("a", now - day, "Ready", items = listOf("CL_SUPERLATIVE · HIGH · cheapest")),
            snap("b", now - 2 * day, "Ready", items = listOf("CL_SUPERLATIVE · HIGH · cheapest"))
        )
        val report = SevenDayPromotionalRiskAnalyzer.analyze(snaps, now)
        assertEquals(listOf("CL_SUPERLATIVE"), report.repeatCodes)
        assertEquals("HIGH", report.riskLevel)
        assertEquals(7, report.projectedRestrictionDays)
        assertTrue(report.items.any { it.contains("CL_SUPERLATIVE") })
        assertTrue(report.recommendation.contains("7-днев"))
    }

    @Test
    fun blockedAigcPackagesRaiseScore() {
        val snaps = listOf(
            snap(
                "x",
                now - 1000,
                "Ready",
                items = listOf("AIGC_NO_DECEIVE · HIGH · real footage"),
                verdict = "BLOCKED",
                publishSafe = false
            )
        )
        val report = SevenDayPromotionalRiskAnalyzer.analyze(snaps, now)
        assertEquals(1, report.blockedPackages)
        assertTrue(report.score >= 20)
        assertTrue(report.projectedRestrictionDays >= 3)
    }

    @Test
    fun reportDoesNotEmbedPromptText() {
        val snaps = listOf(
            snap("p", now, "Ready", items = listOf("PR_PRICE_UI · HIGH · 50% OFF"))
        )
        val report = SevenDayPromotionalRiskAnalyzer.analyze(snaps, now)
        val blob = report.items.joinToString() + report.recommendation
        assertFalse(blob.contains("FORMAT"))
        assertFalse(blob.contains("PRODUCT LOCK"))
        assertFalse(blob.contains("veoPrompt"))
    }

    private fun hour() = 60L * 60 * 1000

    private fun snap(
        id: String,
        updatedAt: Long,
        status: String,
        hasPackage: Boolean = true,
        items: List<String> = emptyList(),
        verdict: String = "",
        publishSafe: Boolean = true
    ) = SevenDayPromotionalRiskAnalyzer.Snapshot(
        id = id,
        updatedAt = updatedAt,
        status = status,
        hasPackage = hasPackage,
        auditItems = items,
        aigcVerdict = verdict,
        aigcShopPublishSafe = publishSafe
    )
}
