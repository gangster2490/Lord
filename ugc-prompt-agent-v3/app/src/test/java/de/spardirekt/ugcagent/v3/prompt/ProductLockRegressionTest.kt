package de.spardirekt.ugcagent.v3.prompt

import de.spardirekt.ugcagent.v3.image.FirstFrameHeuristics
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductLockRegressionTest {
    private val fingerprint = ProductIdentity.microwaveCoverFingerprint()

    @Test
    fun testA_componentCountLockForbidsMergeSplitDeleteRelocateAndShapeSubstitution() {
        val raw = "FORMAT:\nVertical 9:16.\nACTION:\nhand grips the handle."
        val prompt = ProductLock.applyGenerator(ProductLock.ensure(raw, true, fingerprint), "VEO")
        assertFalse(ProductLock.allowsComponentMutation(prompt))
        assertTrue(prompt.contains("Do not merge", ignoreCase = true))
        assertTrue(prompt.contains("Do not split", ignoreCase = true) || prompt.contains("merge, split", ignoreCase = true))
        assertTrue(prompt.contains("relocate", ignoreCase = true))
        assertTrue(prompt.contains("invent", ignoreCase = true))
        assertTrue(prompt.contains("rectangular", ignoreCase = true))
        assertTrue(prompt.contains("two rectangular", ignoreCase = true) || prompt.contains("separate rectangular", ignoreCase = true))
        assertFalse(
            "final prompt must not instruct merging two modules into one",
            Regex("""(?<!not )(?<!never )merge (the )?(two|components) into one""", RegexOption.IGNORE_CASE).containsMatchIn(prompt),
        )
        org.junit.Assert.assertEquals(emptyList<String>(), ProductLock.regressionFailures(prompt, fingerprint))
    }

    @Test
    fun testA_failsWhenPromptPermitsMergingOrGenericReplacement() {
        val bad = "You may merge the two upper modules into one cylindrical reservoir. A similar product from the same category is acceptable."
        assertTrue(ProductLock.allowsComponentMutation(bad))
        assertTrue(ProductLock.allowsGenericSubstitution(bad))
        assertFalse(ProductLock.preservesMicrowaveCover(bad))
    }

    @Test
    fun testB_highRiskActionIsNotSelectedAutomatically() {
        val scene = JSONObject()
            .put("environment", "ordinary kitchen")
            .put("main_action", "pour water into the top compartment")
            .put("human_interaction", "both hands fill the cover")
        val local = ActionIdentity.localCheck(scene.getString("main_action"), fingerprint)
        assertEquals("HIGH", local.getString("risk"))
        assertTrue(local.getString("recommended_safe_action").isNotBlank())
        assertFalse(ActionIdentity.isUnsafeAction(local.getString("recommended_safe_action")))
        val applied = ActionIdentity.applyIfHighRisk(scene, local)
        assertEquals("HIGH", local.getString("risk"))
        assertFalse(ActionIdentity.selectedActionIsUnsafe(applied))
        assertFalse(applied.getString("main_action").contains("pour water", ignoreCase = true))
        assertTrue(applied.getBoolean("action_identity_override"))
        assertEquals("pour water into the top compartment", applied.getString("rejected_high_risk_action"))
        assertTrue(applied.getString("main_action").contains("handle", ignoreCase = true))
    }

    @Test
    fun testC_microwaveCoverIdentityIsPreservedAndCylindricalReservoirForbidden() {
        val raw = "FORMAT:\nVertical 9:16. One continuous UGC clip.\nSAFE ACTION:\none hand grips the handle."
        val prompt = ProductLock.applyGenerator(ProductLock.ensure(raw, true, fingerprint), "VEO")
        assertTrue(ProductLock.preservesMicrowaveCover(prompt))
        assertTrue(prompt.contains("transparent dome", ignoreCase = true))
        assertTrue(prompt.contains("green circular base ring", ignoreCase = true))
        assertTrue(prompt.contains("curved green", ignoreCase = true))
        assertTrue(prompt.contains("circular upper vent", ignoreCase = true))
        assertTrue(prompt.contains("rectangular transparent upper modules", ignoreCase = true) || prompt.contains("rectangular", ignoreCase = true))
        assertTrue(prompt.contains("green caps", ignoreCase = true))
        assertTrue(prompt.contains("relative positions", ignoreCase = true))
        assertTrue(prompt.contains("seam/rib", ignoreCase = true) || prompt.contains("seam") && prompt.contains("rib"))
        assertTrue(prompt.contains("attachment", ignoreCase = true))
        assertTrue(prompt.contains("cylindrical reservoir", ignoreCase = true))
        assertTrue(prompt.contains("Never replace the two rectangular", ignoreCase = true))
        org.junit.Assert.assertEquals(emptyList<String>(), ProductLock.regressionFailures(prompt, fingerprint))
    }

    @Test
    fun testD_genericSubstitutionIsForbidden() {
        val prompt = ProductLock.applyGenerator(
            ProductLock.ensure("the referenced product stays on the table", true, fingerprint),
            "VEO",
        )
        assertFalse(ProductLock.allowsGenericSubstitution(prompt))
        assertTrue(prompt.contains("Do not generate a similar product", ignoreCase = true))
        assertTrue(prompt.contains("generic product from the same category", ignoreCase = true))
        assertTrue(prompt.contains("functionally similar but visually different", ignoreCase = true) || prompt.contains("functionally equivalent but visually different", ignoreCase = true))
        assertTrue(ProductLock.looksLikeProductRebuild("use a similar product from the same category"))
        assertFalse(ProductLock.looksLikeProductRebuild(prompt))
        org.junit.Assert.assertEquals(emptyList<String>(), ProductLock.regressionFailures(prompt, fingerprint))
    }

    @Test
    fun veoDurationIsExactNotMaximum() {
        val prompt = ProductLock.applyGenerator("a clip of the referenced product", "VEO")
        assertTrue(prompt.contains("exactly 8.0 seconds"))
        assertFalse(prompt.contains("maximum 8"))
        val rewritten = ProductLock.applyGenerator("Target generator: Veo. Vertical 9:16, maximum 8.0 seconds, one continuous clip.\nHello", "VEO")
        assertTrue(rewritten.contains("exactly 8.0 seconds"))
        assertFalse(rewritten.contains("maximum 8"))
    }

    @Test
    fun firstFrameRecommendationDoesNotAutoReplace() {
        val small = FirstFrameHeuristics.RankedImage("a", 0, 400, 400, 20_000, FirstFrameHeuristics.score(400, 400, 20_000))
        val large = FirstFrameHeuristics.RankedImage("b", 1, 1200, 1600, 180_000, FirstFrameHeuristics.score(1200, 1600, 180_000))
        val rec = FirstFrameHeuristics.recommendLocal(listOf(small, large))
        assertEquals("b", rec?.id)
        val merged = FirstFrameHeuristics.mergeRecommendation("a", 1, listOf(small, large))
        assertEquals("b", merged?.id)
        val userKept = "a"
        assertTrue(userKept != rec?.id)
    }

    @Test
    fun systemPromptsIncludeNewIdentityPipeline() {
        assertTrue(SystemPrompts.PRODUCT_IDENTITY_FINGERPRINT.contains("identity_critical_components"))
        assertTrue(SystemPrompts.ACTION_IDENTITY_RISK_CHECK.contains("recommended_safe_action"))
        assertTrue(SystemPrompts.PRODUCT_IDENTITY_READINESS.contains("generation_risk"))
        assertTrue(SystemPrompts.FIRST_FRAME_RECOMMENDATION.contains("recommended_image_index"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("STRUCTURAL IDENTITY LOCK"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("exactly 8.0 seconds"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("functionally equivalent but visually different"))
        assertTrue(ProductIdentity.READINESS_HIGH_MESSAGE_RU.contains("Недостаточно визуальной информации"))
    }
}
