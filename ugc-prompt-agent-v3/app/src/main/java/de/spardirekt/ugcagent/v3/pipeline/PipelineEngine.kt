package de.spardirekt.ugcagent.v3.pipeline

import de.spardirekt.ugcagent.v3.ai.PromptContext
import de.spardirekt.ugcagent.v3.compliance.TikTokShopPolicyConfig
import de.spardirekt.ugcagent.v3.data.ImageRules
import de.spardirekt.ugcagent.v3.image.FirstFrameHeuristics
import de.spardirekt.ugcagent.v3.prompt.ActionIdentity
import de.spardirekt.ugcagent.v3.prompt.ProductIdentity
import de.spardirekt.ugcagent.v3.prompt.ProductLock
import org.json.JSONArray
import org.json.JSONObject

class PipelineEngine(private val ai: PipelineAi) {
    fun start(session: PipelineSession): PipelineSession {
        session.pausedReason = null
        session.errorMessage = null
        session.warnings.clear()
        session.completed.clear()
        session.repairApplied = false
        session.firstFrameAutoApplied = false
        session.stage = PipelineStage.IDLE
        session.resumeStage = PipelineStage.IMAGES_READY
        return advance(session)
    }

    fun resume(session: PipelineSession): PipelineSession {
        session.pausedReason = null
        session.errorMessage = null
        if (session.stage == PipelineStage.EXPORT_READY) return session
        val from = session.resumeStage
            ?: session.stage.takeUnless { it == PipelineStage.PAUSED || it == PipelineStage.ERROR }
            ?: PipelineStage.IMAGES_READY
        session.stage = from
        session.resumeStage = from
        return advance(session)
    }

    private fun advance(session: PipelineSession): PipelineSession {
        for (stage in PipelineStage.runnableOrder) {
            if (session.completed.contains(stage)) continue
            try {
                runStage(session, stage)
                session.completed.add(stage)
                session.stage = stage
                session.resumeStage = nextAfter(stage)
            } catch (paused: PipelinePaused) {
                session.stage = PipelineStage.PAUSED
                session.resumeStage = paused.stage
                session.pausedReason = paused.reason
                return session
            } catch (error: Exception) {
                session.stage = PipelineStage.ERROR
                session.resumeStage = stage
                session.errorMessage = error.message ?: error.javaClass.simpleName
                throw error
            }
        }
        session.stage = PipelineStage.EXPORT_READY
        session.resumeStage = null
        session.pausedReason = null
        return session
    }

    private fun nextAfter(stage: PipelineStage): PipelineStage? {
        val order = PipelineStage.runnableOrder
        val idx = order.indexOf(stage)
        return if (idx >= 0 && idx + 1 < order.size) order[idx + 1] else null
    }

    private fun runStage(session: PipelineSession, stage: PipelineStage) {
        when (stage) {
            PipelineStage.IMAGES_READY -> imagesReady(session)
            PipelineStage.CONSISTENCY_CHECK -> consistency(session)
            PipelineStage.PRODUCT_ANALYSIS -> analysis(session)
            PipelineStage.IDENTITY_FINGERPRINT -> fingerprint(session)
            PipelineStage.IDENTITY_READINESS -> readiness(session)
            PipelineStage.FIRST_FRAME -> firstFrame(session)
            PipelineStage.ACTION_RISK, PipelineStage.SCENE_GENERATION -> {
                if (session.scene == null || !session.completed.contains(PipelineStage.SCENE_GENERATION)) {
                    sceneAndRisk(session)
                }
                session.completed.add(PipelineStage.ACTION_RISK)
                session.completed.add(PipelineStage.SCENE_GENERATION)
            }
            PipelineStage.FINAL_IDENTITY_LOCK -> {
                session.finalIdentityLock = ProductIdentity.finalIdentityLockBlock(session.identityFingerprint)
            }
            PipelineStage.PROMPT_GENERATION -> prompt(session)
            PipelineStage.PROMPT_QUALITY_CHECK -> quality(session)
            PipelineStage.COMPLIANCE -> compliance(session)
            PipelineStage.CAPTION -> caption(session)
            PipelineStage.EXPORT_READY -> Unit
            else -> Unit
        }
    }

    private fun imagesReady(session: PipelineSession) {
        if (!session.hasApiKey) {
            throw PipelinePaused(PauseReasons.NO_API_KEY, PipelineStage.IMAGES_READY)
        }
        if (session.images.size < ImageRules.MIN) {
            throw PipelinePaused(PauseReasons.NEED_IMAGES, PipelineStage.IMAGES_READY)
        }
    }

    private fun consistency(session: PipelineSession) {
        val result = ai.consistencyCheck()
        session.consistency = result
        val same = result.optBoolean("same_product", true)
        val confidence = result.optDouble("confidence", 0.0)
        if (!same && !session.consistencyOverride) {
            throw PipelinePaused(PauseReasons.DIFFERENT_PRODUCTS, PipelineStage.CONSISTENCY_CHECK)
        }
        if (confidence < 0.8 && !session.consistencyOverride) {
            throw PipelinePaused(PauseReasons.LOW_CONSISTENCY, PipelineStage.CONSISTENCY_CHECK)
        }
        if (!same || confidence < 0.8) {
            session.warnings.add("Consistency warning: ${result.optString("reason")}")
        }
    }

    private fun analysis(session: PipelineSession) {
        val result = ai.analyseProduct()
        session.analysis = result
        val blob = listOf(
            result.optString("product_category"),
            result.optString("observed_use_case"),
            result.optString("inferred_use_case"),
        ).joinToString(" ")
        if (TikTokShopPolicyConfig.matchesRestrictedCategory(blob)) {
            throw PipelinePaused(PauseReasons.RESTRICTED_CATEGORY, PipelineStage.PRODUCT_ANALYSIS)
        }
        val warning = result.optString("ambiguity_warning")
        if (warning.isNotBlank()) session.warnings.add(warning)
    }

    private fun fingerprint(session: PipelineSession) {
        session.identityFingerprint = ai.fingerprint()
    }

    private fun readiness(session: PipelineSession) {
        val fingerprint = session.identityFingerprint ?: JSONObject()
        val local = ProductIdentity.localReadiness(fingerprint)
        val merged = ProductIdentity.mergeReadiness(local, ai.readiness(fingerprint))
        session.identityReadiness = merged
        if (merged.optString("generation_risk") == "HIGH") {
            throw PipelinePaused(PauseReasons.READINESS_HIGH, PipelineStage.IDENTITY_READINESS)
        }
        if (merged.optString("generation_risk") == "MEDIUM") {
            session.warnings.add("Identity readiness MEDIUM — using a simpler evidenced action.")
        }
    }

    private fun firstFrame(session: PipelineSession) {
        val ranked = session.rankedImages()
        val local = FirstFrameHeuristics.recommendLocal(ranked)
        val aiRec = ai.recommendFirstFrame()
        val chosen = FirstFrameHeuristics.mergeRecommendation(
            local?.id,
            aiRec.optInt("recommended_image_index", -1),
            ranked,
        ) ?: throw PipelinePaused(PauseReasons.NO_USABLE_FIRST_FRAME, PipelineStage.FIRST_FRAME)
        session.recommendedFirstFrameId = chosen.id
        val quality = FirstFrameHeuristics.merge(
            FirstFrameHeuristics.check(chosen.width, chosen.height, chosen.compressedBytes),
            ai.firstFrameQuality(chosen.index),
        )
        session.firstFrameQuality = quality
        val rec = JSONObject()
            .put("recommended_image_index", chosen.index)
            .put("recommended_image_id", chosen.id)
            .put("confidence", FirstFrameHeuristics.recommendationConfidence(quality, aiRec))
            .put("reasons", aiRec.optJSONArray("reasons") ?: JSONArray().put("Largest clean visible product area among uploaded originals."))
            .put("identity_components_visible", aiRec.optBoolean("identity_components_visible", true))
            .put("marketplace_ui_over_product", aiRec.optBoolean("marketplace_ui_over_product", false))
            .put("source", "local+ai")
        session.firstFrameRecommendation = rec
        val confidence = rec.optDouble("confidence", 0.0)
        if (!quality.optBoolean("usable", false)) {
            throw PipelinePaused(PauseReasons.NO_USABLE_FIRST_FRAME, PipelineStage.FIRST_FRAME)
        }
        if (!session.firstFrameUserChosen && FirstFrameHeuristics.shouldPauseForLowConfidence(confidence, quality)) {
            throw PipelinePaused(PauseReasons.LOW_FIRST_FRAME_CONFIDENCE, PipelineStage.FIRST_FRAME)
        }
        if (!session.firstFrameUserChosen && FirstFrameHeuristics.shouldAutoApply(confidence, quality)) {
            session.firstFrameId = chosen.id
            session.firstFrameAutoApplied = true
        } else if (session.firstFrameId.isNullOrBlank()) {
            session.firstFrameId = chosen.id
            session.firstFrameAutoApplied = true
        }
    }

    private fun sceneAndRisk(session: PipelineSession) {
        val analysis = session.analysis ?: throw PipelinePaused(PauseReasons.ANALYSIS_MISSING, PipelineStage.SCENE_GENERATION)
        val fingerprint = session.identityFingerprint ?: JSONObject()
        val generated = ai.generateScene(analysis, fingerprint, session.scene)
        val local = ActionIdentity.localCheck(generated.optString("main_action"), fingerprint)
        val merged = ActionIdentity.merge(local, ai.actionRisk(fingerprint, generated))
        var applied = ActionIdentity.applyIfHighRisk(generated, merged)
        val safer = merged.optString("recommended_safe_action")
        val stillUnsafe = ActionIdentity.selectedActionIsUnsafe(applied) || ActionIdentity.isHighMotionAction(applied.optString("main_action"))
        if (stillUnsafe || (merged.optString("risk") == "HIGH" && ActionIdentity.isUnsafeAction(safer))) {
            throw PipelinePaused(PauseReasons.ONLY_HIGH_RISK, PipelineStage.ACTION_RISK)
        }
        if (merged.optString("risk") == "MEDIUM" && !ActionIdentity.geometryClearlySupported(merged, fingerprint)) {
            applied = ActionIdentity.applyIfHighRisk(
                applied,
                JSONObject(merged.toString()).put("risk", "HIGH").put("motion_geometry_risk", "HIGH"),
            )
            session.warnings.add("MEDIUM action lacked clear geometry support; simplified to a LOW-RISK static action.")
        }
        session.actionRisk = merged
        session.scene = applied
        if (applied.optBoolean("action_identity_override")) {
            session.warnings.add("High-risk action replaced with a safer identity-preserving action.")
        }
    }

    private fun prompt(session: PipelineSession) {
        val fingerprint = session.identityFingerprint ?: JSONObject()
        val lock = session.finalIdentityLock ?: ProductIdentity.finalIdentityLockBlock(fingerprint)
        session.finalIdentityLock = lock
        val ctx = PromptContext(
            analysis = session.analysis?.toString() ?: "{}",
            scene = session.scene?.toString() ?: "{}",
            speechLanguage = session.speechLanguage,
            captionLanguage = session.captionLanguage,
            targetGenerator = session.targetGenerator,
            strictProductLock = session.strictProductLock,
            fingerprint = fingerprint.toString(),
            actionRisk = session.actionRisk?.toString() ?: "{}",
            readiness = session.identityReadiness?.toString() ?: "{}",
            finalIdentityLock = lock,
        )
        var prompt = ai.generatePrompt(ctx)
        prompt = ProductLock.ensure(prompt, session.strictProductLock, fingerprint)
        prompt = ProductLock.applyGenerator(prompt, session.targetGenerator)
        prompt = ProductLock.ensureSpeechTiming(prompt, session.speechLanguage)
        session.finalPrompt = prompt
    }

    private fun quality(session: PipelineSession) {
        val fingerprint = session.identityFingerprint
        var prompt = session.finalPrompt.orEmpty()
        if (!session.repairApplied) {
            prompt = ProductLock.repairOnce(
                prompt,
                fingerprint,
                session.targetGenerator,
                session.speechLanguage,
                session.strictProductLock,
            )
            session.repairApplied = true
        }
        session.finalPrompt = prompt
        ProductLock.regressionFailures(prompt, fingerprint, session.targetGenerator, session.speechLanguage).forEach {
            session.warnings.add("Prompt quality: $it")
        }
    }

    private fun compliance(session: PipelineSession) {
        val prompt = session.finalPrompt.orEmpty()
        val result = ai.checkCompliance(prompt, session.analysis, session.caption.orEmpty(), session.hashtags)
        session.compliance = result
        if (result.optString("status") == "BLOCK") {
            throw PipelinePaused(PauseReasons.COMPLIANCE_BLOCK, PipelineStage.COMPLIANCE)
        }
        val warnings = result.optJSONArray("warnings") ?: JSONArray()
        for (i in 0 until warnings.length()) {
            val text = warnings.optString(i)
            if (text.isNotBlank()) session.warnings.add(text)
        }
    }

    private fun caption(session: PipelineSession) {
        val fingerprint = session.identityFingerprint ?: JSONObject()
        val ctx = PromptContext(
            analysis = session.analysis?.toString() ?: "{}",
            scene = session.scene?.toString() ?: "{}",
            speechLanguage = session.speechLanguage,
            captionLanguage = session.captionLanguage,
            targetGenerator = session.targetGenerator,
            strictProductLock = session.strictProductLock,
            currentPrompt = session.finalPrompt.orEmpty(),
            fingerprint = fingerprint.toString(),
            finalIdentityLock = session.finalIdentityLock.orEmpty(),
        )
        val result = ai.generateCaption(ctx)
        session.caption = result.optString("caption")
        val tags = result.optJSONArray("hashtags") ?: JSONArray()
        session.hashtags = MutableList(tags.length()) { tags.optString(it) }
        val reviewed = ai.checkCompliance(
            session.finalPrompt.orEmpty(),
            session.analysis,
            session.caption.orEmpty(),
            session.hashtags,
        )
        session.compliance = reviewed
        if (reviewed.optString("status") == "BLOCK") {
            throw PipelinePaused(PauseReasons.COMPLIANCE_BLOCK, PipelineStage.CAPTION)
        }
        val warnings = reviewed.optJSONArray("warnings") ?: JSONArray()
        for (i in 0 until warnings.length()) {
            val text = warnings.optString(i)
            if (text.isNotBlank() && text !in session.warnings) session.warnings.add(text)
        }
    }
}

object PauseReasons {
    const val NEED_IMAGES = "NEED_IMAGES"
    const val DIFFERENT_PRODUCTS = "DIFFERENT_PRODUCTS"
    const val LOW_CONSISTENCY = "LOW_CONSISTENCY"
    const val READINESS_HIGH = "READINESS_HIGH"
    const val NO_USABLE_FIRST_FRAME = "NO_USABLE_FIRST_FRAME"
    const val LOW_FIRST_FRAME_CONFIDENCE = "LOW_FIRST_FRAME_CONFIDENCE"
    const val RESTRICTED_CATEGORY = "RESTRICTED_CATEGORY"
    const val COMPLIANCE_BLOCK = "COMPLIANCE_BLOCK"
    const val NO_API_KEY = "NO_API_KEY"
    const val ONLY_HIGH_RISK = "ONLY_HIGH_RISK"
    const val ANALYSIS_MISSING = "ANALYSIS_MISSING"
}
