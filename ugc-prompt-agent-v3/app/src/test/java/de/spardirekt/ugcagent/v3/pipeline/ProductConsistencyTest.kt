package de.spardirekt.ugcagent.v3.pipeline

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductConsistencyTest {
    @Test
    fun viewpointsAndPackagingAreNotHardConflicts() {
        val result = JSONObject()
            .put("same_product", false)
            .put("confidence", 0.96)
            .put("hard_geometry_conflict", false)
            .put("reason", "different viewpoints, packaging image, instruction card and usage demonstration")
            .put("ignored_variation_types", JSONArray().put("viewpoint").put("packaging").put("instruction card"))
        assertFalse(ProductConsistency.shouldPauseForDifferentProducts(result))
        assertTrue(ProductConsistency.looksLikeEvidenceVariation(result))
    }

    @Test
    fun infographicCloseUpAndBackgroundStayOneProduct() {
        val result = JSONObject()
            .put("same_product", false)
            .put("confidence", 0.99)
            .put("reason", "infographic, close-up of the handle, lifestyle background change")
        assertFalse(ProductConsistency.shouldPauseForDifferentProducts(result))
    }

    @Test
    fun lowConfidenceSelectsDominantAndDoesNotPause() {
        val result = JSONObject()
            .put("same_product", false)
            .put("confidence", 0.42)
            .put("hard_geometry_conflict", true)
            .put("reason", "possibly different product geometry")
            .put("dominant_product_indices", JSONArray().put(0).put(1))
            .put("conflicting_image_indices", JSONArray().put(2))
        assertFalse(ProductConsistency.shouldPauseForDifferentProducts(result))
        assertEquals(listOf(0, 1), ProductConsistency.dominantIndices(result, 3))
    }

    @Test
    fun highConfidenceIncompatibleGeometryPauses() {
        val result = JSONObject()
            .put("same_product", false)
            .put("confidence", 0.93)
            .put("hard_geometry_conflict", true)
            .put("reason", "two physically different products with incompatible geometry")
            .put("conflicting_image_indices", JSONArray().put(2))
        assertTrue(ProductConsistency.shouldPauseForDifferentProducts(result))
    }

    @Test
    fun missingHardFlagFallsBackToGeometryLanguage() {
        val result = JSONObject()
            .put("same_product", false)
            .put("confidence", 0.9)
            .put("reason", "incompatible geometry: different shape")
        assertTrue(ProductConsistency.shouldPauseForDifferentProducts(result))
    }

    @Test
    fun uncertainMixedListingWithoutGeometryDoesNotPause() {
        val result = JSONObject()
            .put("same_product", false)
            .put("confidence", 0.88)
            .put("reason", "uncertain mixed listing")
        assertFalse(ProductConsistency.shouldPauseForDifferentProducts(result))
        assertEquals(listOf(0, 1, 3), ProductConsistency.dominantIndices(
            JSONObject(result.toString()).put("conflicting_image_indices", JSONArray().put(2)),
            4,
        ))
    }
}
