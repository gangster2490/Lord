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
    val calls: MutableList<PipelineStage> = mutableListOf(),
) : PipelineAi {
    override fun consistencyCheck(): JSONObject {
        calls.add(PipelineStage.CONSISTENCY_CHECK)
        failIf(PipelineStage.CONSISTENCY_CHECK)
        return JSONObject()
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
            .put("generation_risk", "LOW")
    }

    override fun recommendFirstFrame(): JSONObject {
        calls.add(PipelineStage.FIRST_FRAME)
        failIf(PipelineStage.FIRST_FRAME)
        return JSONObject()
            .put("recommended_image_index", 1)
            .put("reasons", JSONArray().put("largest clean product"))
            .put("identity_components_visible", true)
            .put("marketplace_ui_over_product", false)
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
        if (failAt == stage) throw RuntimeException("fail_$stage")
    }
}
