package de.spardirekt.ugcagent.v3.compliance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokShopPolicyConfigTest {
    @Test
    fun versionIsCentralized() {
        assertEquals("2026.09.1", TikTokShopPolicyConfig.VERSION)
        assertTrue(TikTokShopPolicyConfig.LAST_UPDATED.isNotBlank())
        assertTrue(TikTokShopPolicyConfig.restrictedCategoryKeywords.contains("waffe"))
        assertTrue(TikTokShopPolicyConfig.disclosurePattern.containsMatchIn("Bitte Werbung beachten"))
    }
}
