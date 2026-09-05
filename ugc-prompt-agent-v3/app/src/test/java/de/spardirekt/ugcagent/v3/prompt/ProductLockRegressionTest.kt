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
        assertTrue(prompt.contains("keep it completely static", ignoreCase = true))
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
    fun firstFrameHighConfidenceIsAutoApplied() {
        val small = FirstFrameHeuristics.RankedImage("a", 0, 400, 400, 20_000, FirstFrameHeuristics.score(400, 400, 20_000))
        val large = FirstFrameHeuristics.RankedImage("b", 1, 1200, 1600, 180_000, FirstFrameHeuristics.score(1200, 1600, 180_000))
        val rec = FirstFrameHeuristics.recommendLocal(listOf(small, large))
        assertEquals("b", rec?.id)
        val quality = FirstFrameHeuristics.check(1200, 1600, 180_000)
        assertTrue(FirstFrameHeuristics.shouldAutoApply(quality.getDouble("confidence"), quality))
        val tinyQuality = FirstFrameHeuristics.check(120, 90, 2_000)
        assertTrue(FirstFrameHeuristics.shouldPauseForLowConfidence(tinyQuality.getDouble("confidence"), tinyQuality))
    }

    @Test
    fun systemPromptsIncludeNewIdentityPipeline() {
        assertTrue(SystemPrompts.PRODUCT_IDENTITY_FINGERPRINT.contains("identity_critical_components"))
        assertTrue(SystemPrompts.ACTION_IDENTITY_RISK_CHECK.contains("recommended_safe_action"))
        assertTrue(SystemPrompts.PRODUCT_IDENTITY_READINESS.contains("generation_risk"))
        assertTrue(SystemPrompts.FIRST_FRAME_RECOMMENDATION.contains("recommended_image_index"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("FINAL IDENTITY LOCK"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("exactly 8.0 seconds"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("functionally equivalent but visually different"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("MOVING COMPONENT LOCK"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("freeze-frame tail"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("finish before the 8.0-second endpoint"))
        assertTrue(SystemPrompts.ACTION_IDENTITY_RISK_CHECK.contains("motion_geometry_risk"))
        assertTrue(ProductIdentity.READINESS_HIGH_MESSAGE_RU.contains("Недостаточно визуальной информации"))
    }

    @Test
    fun testE_movingComponentGeometryLock() {
        val prompt = ProductLock.applyGenerator(ProductLock.ensure("hand grips the green handle", true, fingerprint), "VEO")
        assertTrue(ProductLock.hasMovingComponentLock(prompt))
        assertFalse(ProductLock.allowsMovingComponentDeformation(prompt))
        assertTrue(prompt.contains("Do not stretch", ignoreCase = true))
        assertTrue(prompt.contains("keep them stationary", ignoreCase = true) || prompt.contains("keep the component stationary", ignoreCase = true))
        assertTrue(prompt.contains("A static exact component is preferable", ignoreCase = true))
        val bad = "Animate the vent even if uncertain. Stretching and resizing the handle is allowed."
        assertTrue(ProductLock.allowsMovingComponentDeformation(bad))
        org.junit.Assert.assertEquals(emptyList<String>(), ProductLock.regressionFailures(prompt, fingerprint, "VEO", "OFF"))
    }

    @Test
    fun testE_circularUpperStaysStaticWhenMotionUncertain() {
        val scene = JSONObject()
            .put("main_action", "adjust and rotate the circular upper vent")
        val local = ActionIdentity.localCheck(scene.getString("main_action"), fingerprint)
        assertEquals("HIGH", local.getString("motion_geometry_risk"))
        val applied = ActionIdentity.applyIfHighRisk(scene, local)
        assertTrue(applied.getBoolean("moving_component_kept_static"))
        assertFalse(ActionIdentity.isHighMotionAction(applied.getString("main_action")))
        assertTrue(applied.getString("main_action").contains("static", ignoreCase = true) || applied.getString("main_action").contains("handle", ignoreCase = true) || applied.getString("main_action").contains("stationary", ignoreCase = true))
    }

    @Test
    fun testF_veoExactDurationNotMaximum() {
        val prompt = ProductLock.applyGenerator(ProductLock.ensure("a clip of the referenced product", true, fingerprint), "VEO")
        assertTrue(ProductLock.veoHasExactDuration(prompt))
        assertFalse(prompt.contains("maximum 8"))
        assertTrue(prompt.contains("exactly 8.0 seconds"))
        assertTrue(prompt.contains("end at exactly 8.0 seconds", ignoreCase = true))
        assertTrue(prompt.contains("intro", ignoreCase = true) && prompt.contains("outro", ignoreCase = true))
        val rewritten = ProductLock.applyGenerator("Target generator: Veo. Vertical 9:16, maximum 8.0 seconds, one continuous clip.\nHello", "VEO")
        assertTrue(ProductLock.veoHasExactDuration(rewritten))
        assertFalse(rewritten.contains("maximum 8"))
        org.junit.Assert.assertEquals(emptyList<String>(), ProductLock.regressionFailures(prompt, fingerprint, "VEO", "OFF"))
    }

    @Test
    fun testG_speechFinishesBeforeEndpoint() {
        val prompt = ProductLock.ensureSpeechTiming(
            ProductLock.applyGenerator(ProductLock.ensure("the person speaks while holding the product", true, fingerprint), "VEO"),
            "DEUTSCH",
        )
        assertTrue(ProductLock.hasSpeechEndTiming(prompt))
        assertTrue(prompt.contains("The spoken line must finish before the 8.0-second endpoint"))
        org.junit.Assert.assertEquals(emptyList<String>(), ProductLock.regressionFailures(prompt, fingerprint, "VEO", "DEUTSCH"))
        val ru = ProductLock.ensureSpeechTiming("FORMAT:\nclip", "РУССКИЙ")
        assertTrue(ProductLock.hasSpeechEndTiming(ru))
        val off = ProductLock.ensureSpeechTiming("clip", "OFF")
        assertTrue(off.contains("No spoken dialogue"))
        assertFalse(ProductLock.hasSpeechEndTiming(off))
    }

    @Test
    fun testH_noExtraTail() {
        val prompt = ProductLock.applyGenerator(ProductLock.ensure("one micro-moment", true, fingerprint), "VEO")
        assertFalse(ProductLock.allowsExtraTail(prompt))
        assertTrue(prompt.contains("freeze-frame tail", ignoreCase = true))
        assertTrue(prompt.contains("additional action", ignoreCase = true))
        val bad = "Continue with an outro and freeze-frame tail plus additional hold after the main moment."
        assertTrue(ProductLock.allowsExtraTail(bad))
        org.junit.Assert.assertEquals(emptyList<String>(), ProductLock.regressionFailures(prompt, fingerprint, "VEO", "OFF"))
    }

    @Test
    fun testI_utf8CyrillicUnicodeIsPreservedAndMojibakeRepaired() {
        val original = de.spardirekt.ugcagent.v3.text.Utf8Guard.SAMPLE_RU
        assertTrue(original.contains("«"))
        assertTrue(original.contains("тарелку"))
        val broken = de.spardirekt.ugcagent.v3.text.Utf8Guard.simulateLatin1Mojibake(original)
        assertTrue(de.spardirekt.ugcagent.v3.text.Utf8Guard.looksBroken(broken))
        assertFalse(broken.contains("тарелку"))
        val repaired = de.spardirekt.ugcagent.v3.text.Utf8Guard.repair(broken)
        assertEquals(original, repaired)
        val prompt = ProductLock.repairOnce("SPEECH:\n$broken", fingerprint, "VEO", "РУССКИЙ", true)
        assertTrue(prompt.contains("тарелку") || prompt.contains(original) || !de.spardirekt.ugcagent.v3.text.Utf8Guard.looksBroken(prompt))
        val encoded = java.util.Base64.getEncoder().encodeToString(org.json.JSONObject().put("line", original).toString().toByteArray(Charsets.UTF_8))
        val latin1 = String(java.util.Base64.getDecoder().decode(encoded), Charsets.ISO_8859_1)
        val roundTrip = String(latin1.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        assertTrue(roundTrip.contains(original))
    }

    @Test
    fun testJ_finalPromptCleanupStripsUncertaintyAndDuplicateLock() {
        val dirty = """
            ${ProductLock.LOCK_TEXT}

            STRUCTURAL IDENTITY LOCK:
            Uncertain/hidden geometry — do not invent: fill opening
            "uncertain_hidden_geometry": ["internal path"]
            "ambiguity_warning": "finish conflict between photos"

            ${ProductLock.LOCK_TEXT}

            ACTION: grip the handle.
        """.trimIndent()
        val prompt = ProductLock.repairOnce(dirty, fingerprint, "VEO", "OFF", true)
        assertFalse(ProductLock.leaksInternalAnalysis(prompt))
        assertFalse(ProductLock.hasDuplicateProductLock(prompt))
        assertTrue(prompt.contains("FINAL IDENTITY LOCK", ignoreCase = true))
        assertEquals(1, Regex("REFERENCE IMAGE OVERRIDES", RegexOption.IGNORE_CASE).findAll(prompt).count())
        assertFalse(prompt.contains("uncertain_hidden_geometry"))
        assertFalse(prompt.contains("ambiguity_warning"))
        org.junit.Assert.assertEquals(emptyList<String>(), ProductLock.regressionFailures(prompt, fingerprint, "VEO", "OFF"))
    }

    @Test
    fun testK_finishConflictSelectedFirstFrameWins() {
        val conflicted = ProductIdentity.microwaveCoverFingerprint()
            .put("finish_conflict", "one image glossy white, another matte black")
        val raw = "The lid looks white in one photo and black in another. Conflicting finish notes: pick both."
        val prompt = ProductLock.repairOnce(raw, conflicted, "VEO", "OFF", true)
        assertTrue(prompt.contains("SELECTED FIRST FRAME WINS"))
        assertTrue(ProductIdentity.hasFinishConflict(conflicted))
        assertFalse(prompt.contains("finish_conflict"))
        assertFalse(ProductLock.leaksInternalAnalysis(prompt))
    }
}
