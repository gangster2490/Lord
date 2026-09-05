package de.spardirekt.ugcagent.v3.pipeline

import de.spardirekt.ugcagent.v3.ai.PromptContext
import de.spardirekt.ugcagent.v3.compliance.ComplianceEngine
import de.spardirekt.ugcagent.v3.prompt.ProductIdentity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineEngineTest {
    @Test
    fun testL_threeValidImagesAndApiReachExportReadyInOneStart() {
        val fake = FakePipelineAi()
        val engine = PipelineEngine(fake)
        val session = sampleSession()
        val result = engine.start(session)
        assertEquals(PipelineStage.EXPORT_READY, result.stage)
        assertTrue(result.completed.contains(PipelineStage.CONSISTENCY_CHECK))
        assertTrue(result.completed.contains(PipelineStage.IDENTITY_FINGERPRINT))
        assertTrue(result.completed.contains(PipelineStage.FIRST_FRAME))
        assertTrue(result.completed.contains(PipelineStage.PROMPT_QUALITY_CHECK))
        assertTrue(result.completed.contains(PipelineStage.CAPTION))
        assertTrue(result.repairApplied)
        assertTrue(result.finalPrompt.orEmpty().contains("FINAL IDENTITY LOCK"))
        assertEquals("b", result.firstFrameId)
        assertTrue(result.firstFrameAutoApplied)
        assertEquals(1, fake.calls.count { it == PipelineStage.CONSISTENCY_CHECK })
        assertTrue(fake.calls.contains(PipelineStage.PRODUCT_ANALYSIS))
        assertTrue(result.details.orEmpty().contains("Produktkategorie"))
    }

    @Test
    fun testM_resumeAfterFailureDoesNotRestartCompletedStages() {
        val fake = FakePipelineAi(failAt = PipelineStage.ACTION_RISK)
        val engine = PipelineEngine(fake)
        val session = sampleSession()
        try {
            engine.start(session)
        } catch (_: RuntimeException) {
        }
        assertEquals(PipelineStage.ERROR, session.stage)
        assertTrue(session.completed.contains(PipelineStage.CONSISTENCY_CHECK))
        assertTrue(session.completed.contains(PipelineStage.PRODUCT_ANALYSIS))
        assertTrue(session.completed.contains(PipelineStage.IDENTITY_FINGERPRINT))
        assertFalse(session.completed.contains(PipelineStage.SCENE_GENERATION))
        val firstConsistency = fake.calls.count { it == PipelineStage.CONSISTENCY_CHECK }
        val firstAnalysis = fake.calls.count { it == PipelineStage.PRODUCT_ANALYSIS }
        assertEquals(1, firstConsistency)
        fake.failAt = null
        val resumed = engine.resume(session)
        assertEquals(PipelineStage.EXPORT_READY, resumed.stage)
        assertEquals(firstConsistency, fake.calls.count { it == PipelineStage.CONSISTENCY_CHECK })
        assertEquals(firstAnalysis, fake.calls.count { it == PipelineStage.PRODUCT_ANALYSIS })
        assertTrue(fake.calls.count { it == PipelineStage.SCENE_GENERATION } >= 2)
    }

    @Test
    fun lowConsistencyIsWarningNotPause() {
        val fake = FakePipelineAi()
        fake.consistency = org.json.JSONObject()
            .put("same_product", true)
            .put("confidence", 0.41)
            .put("conflicting_image_indices", org.json.JSONArray())
            .put("reason", "color variants and repeated images")
        val result = PipelineEngine(fake).start(sampleSession())
        assertEquals(PipelineStage.EXPORT_READY, result.stage)
        assertTrue(result.warnings.any { it.contains("Dominant product identity") || it.contains("Consistency warning") })
        assertTrue(result.warnings.any { it.contains("Color/finish") })
        assertTrue(result.details.orEmpty().contains("Produktkategorie"))
        assertFalse(result.details.orEmpty().contains("{"))
    }

    @Test
    fun differentProductGeometryStillPauses() {
        val fake = FakePipelineAi()
        fake.consistency = org.json.JSONObject()
            .put("same_product", false)
            .put("confidence", 0.94)
            .put("hard_geometry_conflict", true)
            .put("conflicting_image_indices", org.json.JSONArray().put(2))
            .put("reason", "two physically different products with incompatible geometry")
        val result = PipelineEngine(fake).start(sampleSession())
        assertEquals(PipelineStage.PAUSED, result.stage)
        assertEquals(PauseReasons.DIFFERENT_PRODUCTS, result.pausedReason)
    }

    @Test
    fun mixedEvidenceViewsDoNotPause() {
        val fake = FakePipelineAi()
        fake.consistency = org.json.JSONObject()
            .put("same_product", false)
            .put("confidence", 0.97)
            .put("hard_geometry_conflict", false)
            .put("ignored_variation_types", org.json.JSONArray().put("viewpoint").put("packaging").put("infographic").put("usage demonstration"))
            .put("dominant_product_indices", org.json.JSONArray().put(1).put(2))
            .put("reason", "different viewpoints, packaging image, instruction card, close-up and background change")
        val result = PipelineEngine(fake).start(sampleSession())
        assertEquals(PipelineStage.EXPORT_READY, result.stage)
        assertEquals(listOf(1, 2), result.dominantImageIndices)
        assertTrue(result.warnings.any { it.contains("Dominant product identity") || it.contains("Mixed evidence") })
    }

    @Test
    fun lowConfidenceHardConflictAutoSelectsDominantAndContinues() {
        val fake = FakePipelineAi()
        fake.consistency = org.json.JSONObject()
            .put("same_product", false)
            .put("confidence", 0.2)
            .put("hard_geometry_conflict", true)
            .put("conflicting_image_indices", org.json.JSONArray().put(2))
            .put("dominant_product_indices", org.json.JSONArray().put(0).put(1))
            .put("reason", "different product geometry")
        val result = PipelineEngine(fake).start(sampleSession())
        assertEquals(PipelineStage.EXPORT_READY, result.stage)
        assertEquals(listOf(0, 1), result.dominantImageIndices)
        assertTrue(result.warnings.any { it.contains("Dominant product identity") })
    }

    @Test
    fun prefersProductPhotoOverScreenshotLikeFrame() {
        val fake = FakePipelineAi()
        fake.firstFrameIndex = 0
        fake.firstFrameReasons = org.json.JSONArray().put("marketplace description page")
        val session = sampleSession()
        session.images = listOf(
            PipelineImage("shot", 0, 1080, 2400, 40_000),
            PipelineImage("photo", 1, 1200, 1600, 180_000),
            PipelineImage("alt", 2, 900, 1200, 90_000),
        )
        val result = PipelineEngine(fake).start(session)
        assertEquals(PipelineStage.EXPORT_READY, result.stage)
        assertEquals("photo", result.firstFrameId)
        assertTrue(result.warnings.any { it.contains("screenshot") || it.contains("product photo") })
    }

    @Test
    fun transientNetworkErrorRetriesOnceThenSucceeds() {
        val fake = FakePipelineAi(
            failAt = PipelineStage.CONSISTENCY_CHECK,
            failWith = de.spardirekt.ugcagent.v3.ai.ProviderException.network(),
            failTimes = 1,
        )
        val result = PipelineEngine(fake).start(sampleSession())
        assertEquals(PipelineStage.EXPORT_READY, result.stage)
        assertEquals(2, fake.calls.count { it == PipelineStage.CONSISTENCY_CHECK })
        assertTrue(result.autoRetried)
        assertTrue(result.warnings.any { it.contains("automatic retry") })
    }

    @Test
    fun transientNetworkErrorShowsErrorAfterOneRetry() {
        val fake = FakePipelineAi(
            failAt = PipelineStage.CONSISTENCY_CHECK,
            failWith = de.spardirekt.ugcagent.v3.ai.ProviderException.network(),
            failTimes = 2,
        )
        val session = sampleSession()
        try {
            PipelineEngine(fake).start(session)
            org.junit.Assert.fail("expected network error")
        } catch (e: de.spardirekt.ugcagent.v3.ai.ProviderException) {
            assertEquals("NETWORK", e.code)
        }
        assertEquals(PipelineStage.ERROR, session.stage)
        assertEquals(2, fake.calls.count { it == PipelineStage.CONSISTENCY_CHECK })
    }

    @Test
    fun highReadinessContinuesWithStaticAction() {
        val fake = FakePipelineAi()
        fake.readinessRisk = "HIGH"
        val result = PipelineEngine(fake).start(sampleSession())
        assertEquals(PipelineStage.EXPORT_READY, result.stage)
        assertTrue(result.forceStaticAction)
        assertTrue(result.warnings.any { it.contains("HIGH") })
        assertTrue(result.scene?.optString("rationale").orEmpty().contains("static"))
    }

    @Test
    fun russianLanguageWritesRussianDetails() {
        val session = sampleSession()
        session.speechLanguage = "РУССКИЙ"
        session.captionLanguage = "РУССКИЙ"
        val result = PipelineEngine(FakePipelineAi()).start(session)
        assertTrue(result.details.orEmpty().contains("Категория товара"))
        val pack = de.spardirekt.ugcagent.v3.prompt.DetailsBuilder.videoPackage(
            result.details.orEmpty(),
            result.finalPrompt.orEmpty(),
            result.caption.orEmpty(),
            result.hashtags,
        )
        assertTrue(pack.startsWith(result.details.orEmpty().trim()))
        assertTrue(pack.contains(result.finalPrompt.orEmpty().trim()))
        assertTrue(pack.contains(result.caption.orEmpty().trim()))
    }

    private fun sampleSession(): PipelineSession {
        val session = PipelineSession()
        session.hasApiKey = true
        session.speechLanguage = "DEUTSCH"
        session.targetGenerator = "VEO"
        session.strictProductLock = true
        session.images = listOf(
            PipelineImage("a", 0, 800, 1200, 80_000),
            PipelineImage("b", 1, 1200, 1600, 180_000),
            PipelineImage("c", 2, 900, 1200, 90_000),
        )
        return session
    }
}

class FakePipelineAi(
    var failAt: PipelineStage? = null,
    var failWith: Exception? = null,
    var failTimes: Int = 1,
    val calls: MutableList<PipelineStage> = mutableListOf(),
) : PipelineAi {
    var consistency: JSONObject? = null
    var firstFrameIndex: Int = 1
    var firstFrameReasons: JSONArray = JSONArray().put("largest clean product")
    var readinessRisk: String = "LOW"

    override fun consistencyCheck(): JSONObject {
        calls.add(PipelineStage.CONSISTENCY_CHECK)
        failIf(PipelineStage.CONSISTENCY_CHECK)
        return consistency ?: JSONObject()
            .put("same_product", true)
            .put("confidence", 0.95)
            .put("conflicting_image_indices", JSONArray())
            .put("reason", "same product")
    }

    override fun analyseProduct(): JSONObject {
        calls.add(PipelineStage.PRODUCT_ANALYSIS)
        failIf(PipelineStage.PRODUCT_ANALYSIS)
        return JSONObject()
            .put("product_category", "kitchen")
            .put("observed_use_case", "cover food")
            .put("observed_context", "kitchen")
            .put("ambiguity_warning", "")
    }

    override fun fingerprint(): JSONObject {
        calls.add(PipelineStage.IDENTITY_FINGERPRINT)
        failIf(PipelineStage.IDENTITY_FINGERPRINT)
        return ProductIdentity.microwaveCoverFingerprint()
    }

    override fun readiness(fingerprint: JSONObject): JSONObject {
        calls.add(PipelineStage.IDENTITY_READINESS)
        failIf(PipelineStage.IDENTITY_READINESS)
        return JSONObject()
            .put("score", 0.9)
            .put("missing_views", JSONArray())
            .put("ambiguous_components", JSONArray())
            .put("generation_risk", readinessRisk)
    }

    override fun recommendFirstFrame(): JSONObject {
        calls.add(PipelineStage.FIRST_FRAME)
        failIf(PipelineStage.FIRST_FRAME)
        return JSONObject()
            .put("recommended_image_index", firstFrameIndex)
            .put("reasons", firstFrameReasons)
            .put("identity_components_visible", firstFrameIndex != 0)
            .put("marketplace_ui_over_product", firstFrameIndex == 0)
            .put("confidence", 0.9)
    }

    override fun firstFrameQuality(imageIndex: Int): JSONObject {
        return JSONObject().put("usable", true).put("confidence", 0.86).put("warnings", JSONArray())
    }

    override fun generateScene(analysis: JSONObject, fingerprint: JSONObject, previous: JSONObject?): JSONObject {
        calls.add(PipelineStage.SCENE_GENERATION)
        failIf(PipelineStage.ACTION_RISK)
        failIf(PipelineStage.SCENE_GENERATION)
        return JSONObject()
            .put("environment", "ordinary kitchen")
            .put("camera_entry", "handheld smartphone")
            .put("main_action", "one hand grips the existing green handle and lifts the assembled cover onto a plate")
            .put("human_interaction", "one hand")
            .put("rationale", "low risk")
    }

    override fun actionRisk(fingerprint: JSONObject, scene: JSONObject): JSONObject {
        calls.add(PipelineStage.ACTION_RISK)
        return JSONObject()
            .put("risk", "LOW")
            .put("risk_reasons", JSONArray())
            .put("geometry_that_must_move", JSONArray())
            .put("identity_critical_moving_components", JSONArray())
            .put("hidden_geometry_required", JSONArray())
            .put("motion_geometry_risk", "LOW")
            .put("recommended_safe_action", "one hand grips the existing green handle")
    }

    override fun generatePrompt(ctx: PromptContext): String {
        calls.add(PipelineStage.PROMPT_GENERATION)
        failIf(PipelineStage.PROMPT_GENERATION)
        return "FORMAT:\nVertical 9:16.\nACTION:\none hand grips the handle.\nSPEECH:\nKurz den Deckel auf den Teller."
    }

    override fun checkCompliance(prompt: String, analysis: JSONObject?, caption: String, hashtags: List<String>): JSONObject {
        calls.add(PipelineStage.COMPLIANCE)
        failIf(PipelineStage.COMPLIANCE)
        return ComplianceEngine.review(prompt, prompt, caption, hashtags, analysis, null)
    }

    override fun generateCaption(ctx: PromptContext): JSONObject {
        calls.add(PipelineStage.CAPTION)
        failIf(PipelineStage.CAPTION)
        return JSONObject()
            .put("caption", "Werbung\nDeckel einfach auf den Teller.")
            .put("hashtags", JSONArray().put("#tiktokshop").put("#küche"))
    }

    private fun failIf(stage: PipelineStage) {
        if (failAt == stage && failTimes > 0) {
            failTimes--
            throw failWith ?: RuntimeException("fail_$stage")
        }
    }
}
