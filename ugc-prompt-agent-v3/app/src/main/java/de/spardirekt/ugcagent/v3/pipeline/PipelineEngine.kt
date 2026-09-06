package de.spardirekt.ugcagent.v3.pipeline

import de.spardirekt.ugcagent.v3.ai.PromptContext
import de.spardirekt.ugcagent.v3.ai.ProviderException
import de.spardirekt.ugcagent.v3.compliance.TikTokShopPolicyConfig
import de.spardirekt.ugcagent.v3.data.ImageRules
import de.spardirekt.ugcagent.v3.image.FirstFrameHeuristics
import de.spardirekt.ugcagent.v3.prompt.ActionIdentity
import de.spardirekt.ugcagent.v3.prompt.CreativeStrategyEngine
import de.spardirekt.ugcagent.v3.prompt.DetailsBuilder
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
        session.autoRetried = false
        session.forceStaticAction = false
        session.details = null
        session.dominantImageIndices = emptyList()
        session.stage = PipelineStage.IDLE
        session.resumeStage = PipelineStage.IMAGES_READY
        return advance(session)
    }

    fun resume(session: PipelineSession): PipelineSession {
        session.pausedReason = null
        session.errorMessage = null
        if (session.stage == PipelineStage.READY || session.stage == PipelineStage.EXPORT_READY) return session
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
                if (isTransient(error) && !session.autoRetried) {
                    session.autoRetried = true
                    session.warnings.add("Temporary provider error — automatic retry once.")
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
                    } catch (retryError: Exception) {
                        session.stage = PipelineStage.ERROR
                        session.resumeStage = stage
                        session.errorMessage = retryError.message ?: retryError.javaClass.simpleName
                        throw retryError
                    }
                } else {
                    session.stage = PipelineStage.ERROR
                    session.resumeStage = stage
                    session.errorMessage = error.message ?: error.javaClass.simpleName
                    throw error
                }
            }
        }
        session.stage = PipelineStage.READY
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
            PipelineStage.PRODUCT_ANALYSIS -> analysis(session)
            PipelineStage.IDENTITY_EXTRACTION, PipelineStage.IDENTITY_FINGERPRINT -> fingerprint(session)
            PipelineStage.EVIDENCE_VALIDATION, PipelineStage.CONSISTENCY_CHECK, PipelineStage.IDENTITY_READINESS -> {
                consistency(session)
                readiness(session)
                session.evidence = de.spardirekt.ugcagent.v3.prompt.EvidenceModel.classify(session.analysis)
            }
            PipelineStage.FIRST_FRAME_SELECTION, PipelineStage.FIRST_FRAME -> firstFrame(session)
            PipelineStage.MOTION_RISK_SELECTION, PipelineStage.ACTION_RISK, PipelineStage.SCENE_GENERATION, PipelineStage.FINAL_IDENTITY_LOCK -> {
                sceneAndRisk(session)
                session.finalIdentityLock = ProductIdentity.finalIdentityLockBlock(session.identityFingerprint)
            }
            PipelineStage.HOOK_GENERATION -> hook(session)
            PipelineStage.VEO_PROMPT_GENERATION, PipelineStage.PROMPT_GENERATION -> prompt(session)
            PipelineStage.CAPTION_GENERATION, PipelineStage.CAPTION -> caption(session)
            PipelineStage.HASHTAG_GENERATION -> hashtags(session)
            PipelineStage.FINAL_QUALITY_CHECK, PipelineStage.PROMPT_QUALITY_CHECK, PipelineStage.COMPLIANCE -> {
                quality(session)
                compliance(session)
                selfCheck(session)
                session.details = DetailsBuilder.build(session)
            }
            PipelineStage.READY, PipelineStage.EXPORT_READY -> Unit
            else -> Unit
        }
    }

    private fun isTransient(error: Exception): Boolean {
        return error is ProviderException && error.code in setOf("NETWORK", "TIMEOUT", "RATE_LIMIT")
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
        val reason = result.optString("reason")
        session.dominantImageIndices = ProductConsistency.dominantIndices(result, session.images.size)
        if (ProductConsistency.shouldPauseForDifferentProducts(result) && !session.consistencyOverride) {
            throw PipelinePaused(PauseReasons.DIFFERENT_PRODUCTS, PipelineStage.EVIDENCE_VALIDATION)
        }
        if (!same || confidence < ProductConsistency.HARD_CONFLICT_THRESHOLD) {
            session.warnings.add(ProductConsistency.autoSelectWarning(result))
        } else if (confidence < 0.8) {
            session.warnings.add("Consistency warning: ${reason.ifBlank { "low confidence or mixed references" }}")
        }
        if (ProductConsistency.looksLikeEvidenceVariation(result)) {
            session.warnings.add("Mixed evidence views (packaging, instructions, close-ups, usage, backgrounds) kept as one product.")
        }
        if (reason.contains("color", true) || reason.contains("finish", true) || reason.contains("variant", true)) {
            session.warnings.add("Color/finish variants detected — selected First Frame wins.")
        }
        if (reason.contains("size", true) || reason.contains("dimension", true)) {
            session.warnings.add("Size conflict in references — visual identity from the First Frame wins.")
        }
        warnDuplicates(session, result)
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
        val textClaims = de.spardirekt.ugcagent.v3.ai.JsonExtractor.stringList(result, "text_claims")
        if (textClaims.isNotEmpty()) {
            session.warnings.add("Unverified seller/text claims kept separate from visual evidence.")
        }
        val dimensions = de.spardirekt.ugcagent.v3.ai.JsonExtractor.stringList(result, "dimensions")
        if (dimensions.map { it.trim().lowercase() }.distinct().size > 1) {
            session.warnings.add("Conflicting size/dimension text — not used as identity.")
        }
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
            session.forceStaticAction = true
            session.warnings.add("Identity readiness HIGH — continuing with a static LOW-RISK action.")
        }
        if (merged.optString("generation_risk") == "MEDIUM") {
            session.warnings.add("Identity readiness MEDIUM — using a simpler evidenced action.")
        }
    }

    private fun firstFrame(session: PipelineSession) {
        val ranked = session.rankedImages()
        val local = FirstFrameHeuristics.recommendLocal(ranked)
        val aiRec = ai.recommendFirstFrame()
        val preferred = ranked.firstOrNull { image ->
            val quality = FirstFrameHeuristics.check(image.width, image.height, image.compressedBytes)
            val usablePhoto = quality.optBoolean("usable", false) &&
                !FirstFrameHeuristics.looksLikeScreenshot(image.width, image.height, image.compressedBytes)
            usablePhoto && session.isDominantIndex(image.index)
        } ?: ranked.firstOrNull { image ->
            val quality = FirstFrameHeuristics.check(image.width, image.height, image.compressedBytes)
            quality.optBoolean("usable", false) && !FirstFrameHeuristics.looksLikeScreenshot(image.width, image.height, image.compressedBytes)
        }
        var chosen = FirstFrameHeuristics.mergeRecommendation(
            local?.id,
            aiRec.optInt("recommended_image_index", -1),
            ranked,
        )
        if (chosen != null && FirstFrameHeuristics.rejectAsFirstFrame(aiRec) && preferred != null) {
            session.warnings.add("Skipped text/marketplace screenshot — using a clean product photo as First Frame.")
            chosen = preferred
        }
        if (chosen != null && FirstFrameHeuristics.looksLikeScreenshot(chosen.width, chosen.height, chosen.compressedBytes) && preferred != null && preferred.id != chosen.id) {
            session.warnings.add("Preferred a product photo over a screenshot-like frame.")
            chosen = preferred
        }
        chosen = chosen ?: preferred ?: throw PipelinePaused(PauseReasons.NO_USABLE_FIRST_FRAME, PipelineStage.FIRST_FRAME_SELECTION)
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
        if (!quality.optBoolean("usable", false)) {
            val fallback = preferred ?: ranked.firstOrNull {
                FirstFrameHeuristics.check(it.width, it.height, it.compressedBytes).optBoolean("usable", false)
            }
            if (fallback == null) {
                throw PipelinePaused(PauseReasons.NO_USABLE_FIRST_FRAME, PipelineStage.FIRST_FRAME_SELECTION)
            }
            chosen = fallback
            session.recommendedFirstFrameId = chosen.id
        }
        session.firstFrameId = chosen.id
        session.firstFrameAutoApplied = !session.firstFrameUserChosen
    }

    private fun sceneAndRisk(session: PipelineSession) {
        val analysis = session.analysis ?: throw PipelinePaused(PauseReasons.ANALYSIS_MISSING, PipelineStage.MOTION_RISK_SELECTION)
        val fingerprint = session.identityFingerprint ?: JSONObject()
        val generated = if (session.forceStaticAction) {
            JSONObject()
                .put("environment", CreativeStrategyEngine.plan(analysis, fingerprint).setting)
                .put("camera_entry", "natural handheld smartphone")
                .put("main_action", ActionIdentity.recommendedSafeAction(fingerprint, analysis))
                .put("human_interaction", "simple contact")
                .put("rationale", "static LOW-RISK fallback")
        } else {
            ai.generateScene(analysis, fingerprint, session.scene)
        }
        val plan = CreativeStrategyEngine.plan(analysis, fingerprint)
        val local = ActionIdentity.localCheck(generated.optString("main_action"), fingerprint)
        val merged = ActionIdentity.merge(local, ai.actionRisk(fingerprint, generated))
        var applied = ActionIdentity.applyIfHighRisk(generated, merged)
        if (CreativeStrategyEngine.settingConflicts(applied.optString("environment"), plan)) {
            applied.put("environment", plan.setting)
        }
        if (applied.optString("environment").isBlank()) applied.put("environment", plan.setting)
        applied.put("human_interaction", plan.human)
        val currentAction = applied.optString("main_action")
        if (CreativeStrategyEngine.actionConflicts(currentAction, plan) || currentAction.isBlank()) {
            applied.put("main_action", plan.action)
        }
        val safer = merged.optString("recommended_safe_action")
        val stillUnsafe = ActionIdentity.selectedActionIsUnsafe(applied) || ActionIdentity.isHighMotionAction(applied.optString("main_action"))
        if (stillUnsafe || (merged.optString("risk") == "HIGH" && ActionIdentity.isUnsafeAction(safer))) {
            throw PipelinePaused(PauseReasons.ONLY_HIGH_RISK, PipelineStage.MOTION_RISK_SELECTION)
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
        session.finalPrompt = ProductLock.finalizeClean(
            prompt,
            fingerprint,
            session.targetGenerator,
            session.speechLanguage,
            session.hook,
            session.strictProductLock,
            session.analysis,
            session.evidence,
        )
    }

    private fun hook(session: PipelineSession) {
        val plan = CreativeStrategyEngine.plan(session.analysis, session.identityFingerprint)
        val raw = de.spardirekt.ugcagent.v3.prompt.HookEngine.generate(session.analysis, session.speechLanguage, session.identityFingerprint, plan)
        session.hook = de.spardirekt.ugcagent.v3.prompt.HookEngine.ensureStrong(raw, session.analysis, session.speechLanguage, session.identityFingerprint, plan)
        session.hookScore = de.spardirekt.ugcagent.v3.prompt.HookEngine.qualityScore(session.hook, session.speechLanguage, session.analysis)
        if (session.hookScore < 0.6) {
            session.hook = de.spardirekt.ugcagent.v3.prompt.HookEngine.generate(session.analysis, session.speechLanguage, session.identityFingerprint, plan)
            session.hookScore = de.spardirekt.ugcagent.v3.prompt.HookEngine.qualityScore(session.hook, session.speechLanguage, session.analysis)
        }
    }

    private fun quality(session: PipelineSession) {
        val fingerprint = session.identityFingerprint
        var prompt = session.finalPrompt.orEmpty()
        if (session.hook.isNotBlank() && de.spardirekt.ugcagent.v3.prompt.HookEngine.isWeak(session.hook, session.speechLanguage, session.analysis)) {
            session.hook = de.spardirekt.ugcagent.v3.prompt.HookEngine.generate(session.analysis, session.speechLanguage, session.identityFingerprint)
        }
        if (ProductLock.hasConflictingSpokenHooks(prompt)) {
            session.hook = de.spardirekt.ugcagent.v3.prompt.HookEngine.generate(session.analysis, session.speechLanguage, session.identityFingerprint)
        }
        session.finalPrompt = ProductLock.finalizeClean(
            prompt,
            fingerprint,
            session.targetGenerator,
            session.speechLanguage,
            session.hook,
            session.strictProductLock,
            session.analysis,
            session.evidence,
        )
        if (!de.spardirekt.ugcagent.v3.prompt.PromptComposer.isCanonical(
                session.finalPrompt.orEmpty(),
                session.speechLanguage,
                session.analysis,
                session.evidence,
                fingerprint,
            )
        ) {
            session.finalPrompt = ProductLock.finalizeClean(
                de.spardirekt.ugcagent.v3.prompt.PromptComposer.extractAction(session.finalPrompt.orEmpty()),
                fingerprint,
                session.targetGenerator,
                session.speechLanguage,
                session.hook,
                session.strictProductLock,
                session.analysis,
                session.evidence,
            )
        }
        session.repairApplied = true
        val plan = CreativeStrategyEngine.plan(session.analysis, fingerprint)
        val creativeFails = CreativeStrategyEngine.gateFailures(
            session.finalPrompt.orEmpty(),
            session.hook,
            plan,
            session.speechLanguage,
            session.analysis,
        )
        if (creativeFails.isNotEmpty()) {
            session.hook = de.spardirekt.ugcagent.v3.prompt.HookEngine.generate(
                session.analysis,
                session.speechLanguage,
                fingerprint,
                plan,
            )
            session.finalPrompt = ProductLock.finalizeClean(
                session.finalPrompt.orEmpty(),
                fingerprint,
                session.targetGenerator,
                session.speechLanguage,
                session.hook,
                session.strictProductLock,
                session.analysis,
                session.evidence,
            )
        }
        ProductLock.regressionFailures(session.finalPrompt.orEmpty(), fingerprint, session.targetGenerator, session.speechLanguage).forEach {
            session.warnings.add("Prompt quality: $it")
        }
    }

    private fun compliance(session: PipelineSession) {
        val language = session.captionLanguage.ifBlank { session.speechLanguage }
        val fixed = de.spardirekt.ugcagent.v3.compliance.ComplianceEngine.enforceAndFix(
            prompt = session.finalPrompt.orEmpty(),
            caption = session.caption.orEmpty(),
            hashtags = session.hashtags,
            analysis = session.analysis,
            evidence = session.evidence,
            fingerprint = session.identityFingerprint,
            language = language,
            commercialCaption = de.spardirekt.ugcagent.v3.prompt.CaptionEngine.isCommercialLanguage(language),
        )
        session.finalPrompt = ProductLock.finalizeClean(
            fixed.prompt,
            session.identityFingerprint,
            session.targetGenerator,
            session.speechLanguage,
            session.hook,
            session.strictProductLock,
            session.analysis,
            session.evidence,
        )
        session.caption = fixed.caption
        session.hashtags = fixed.hashtags.toMutableList()
        session.compliance = fixed.review
        if (fixed.review.optString("status") == "BLOCK") {
            throw PipelinePaused(PauseReasons.COMPLIANCE_BLOCK, PipelineStage.FINAL_QUALITY_CHECK)
        }
        val notes = fixed.review.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notes.length()) {
            val text = notes.optString(i)
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
        session.caption = de.spardirekt.ugcagent.v3.prompt.CaptionEngine.finalize(
            raw = result.optString("caption"),
            analysis = session.analysis,
            evidence = session.evidence,
            fingerprint = fingerprint,
            language = session.captionLanguage.ifBlank { session.speechLanguage },
            appendDisclosure = true,
        )
        val tags = result.optJSONArray("hashtags") ?: JSONArray()
        session.hashtags = de.spardirekt.ugcagent.v3.prompt.EvidenceModel.sanitizeHashtags(
            MutableList(tags.length()) { tags.optString(it) },
        ).toMutableList()
    }

    private fun hashtags(session: PipelineSession) {
        val tags = session.hashtags.map { it.trim() }.filter { it.isNotBlank() }.distinct().toMutableList()
        if (tags.size < 4) {
            val extras = CreativeStrategyEngine.hashtagExtras(
                CreativeStrategyEngine.plan(session.analysis, session.identityFingerprint),
                session.speechLanguage.equals("РУССКИЙ", true),
            )
            extras.forEach { if (tags.size < 6 && it !in tags) tags.add(it) }
        }
        session.hashtags = tags.take(6).toMutableList()
    }

    private fun selfCheck(session: PipelineSession) {
        var prompt = session.finalPrompt.orEmpty()
        val composer = de.spardirekt.ugcagent.v3.prompt.PromptComposer
        if (!composer.isCanonical(prompt, session.speechLanguage, session.analysis, session.evidence, session.identityFingerprint) ||
            composer.CANONICAL_HEADINGS.any { (composer.headingCounts(prompt)[it] ?: 0) != 1 }
        ) {
            prompt = ProductLock.finalizeClean(
                prompt,
                session.identityFingerprint,
                session.targetGenerator,
                session.speechLanguage,
                session.hook,
                session.strictProductLock,
                session.analysis,
                session.evidence,
            )
            session.finalPrompt = prompt
            session.repairApplied = true
        }
        val counts = composer.headingCounts(prompt)
        val checks = JSONObject()
            .put("first_frame", session.firstFrameId != null)
            .put("identity_lock", prompt.contains("PRODUCT IDENTITY LOCK", ignoreCase = true))
            .put("one_lock_section", (counts["PRODUCT IDENTITY LOCK"] ?: 0) == 1)
            .put("canonical_headings", composer.CANONICAL_HEADINGS.all { (counts[it] ?: 0) == 1 })
            .put("no_forbidden_headings", composer.FORBIDDEN_HEADINGS.none { (composer.rawHeadingCounts(prompt)[it] ?: 0) > 0 })
            .put("exact_8s", ProductLock.veoHasExactDuration(prompt) || session.targetGenerator != "VEO")
            .put("speech_end", session.speechLanguage.equals("OFF", true) || ProductLock.hasSpeechEndTiming(prompt))
            .put("hook", session.hook.isNotBlank() && !de.spardirekt.ugcagent.v3.prompt.HookEngine.isWeak(session.hook, session.speechLanguage, session.analysis))
            .put("caption", session.caption.orEmpty().isNotBlank())
            .put("hashtags", de.spardirekt.ugcagent.v3.prompt.EvidenceModel.hashtagCountOk(session.hashtags))
            .put("no_hard_error", session.pausedReason == null)
            .put("speech_once", de.spardirekt.ugcagent.v3.prompt.ProductLock.speechHeadingCount(prompt) == 1)
            .put("moving_once", de.spardirekt.ugcagent.v3.prompt.ProductLock.movingLockCount(prompt) == 1)
            .put("anti_morph_once", de.spardirekt.ugcagent.v3.prompt.ProductLock.antiMorphHeadingCount(prompt) == 1)
            .put("timing_once", de.spardirekt.ugcagent.v3.prompt.ProductLock.durationHeadingCount(prompt) == 1)
            .put("no_duration_heading", de.spardirekt.ugcagent.v3.prompt.ProductLock.leftoverDurationCount(prompt) == 0)
            .put("one_spoken_hook", !de.spardirekt.ugcagent.v3.prompt.ProductLock.hasConflictingSpokenHooks(prompt))
            .put("no_foreign_components", !de.spardirekt.ugcagent.v3.prompt.ProductLexicon.containsForeign(prompt, session.identityFingerprint, session.analysis))
            .put("ends_with_timing", composer.endsWithTiming(prompt))
            .put("one_duration_instruction", composer.durationPhraseCount(prompt) <= 1)
            .put("compliance_pass", session.compliance?.optString("status") != "BLOCK")
        session.selfCheck = checks
        if (!checks.optBoolean("canonical_headings") || !checks.optBoolean("one_lock_section") || !checks.optBoolean("speech_once") ||
            !checks.optBoolean("no_foreign_components") || !checks.optBoolean("ends_with_timing")
        ) {
            session.finalPrompt = ProductLock.finalizeClean(
                prompt,
                session.identityFingerprint,
                session.targetGenerator,
                session.speechLanguage,
                session.hook,
                session.strictProductLock,
                session.analysis,
                session.evidence,
            )
            session.repairApplied = true
        }
        if (!checks.optBoolean("hook")) {
            session.hook = de.spardirekt.ugcagent.v3.prompt.HookEngine.generate(session.analysis, session.speechLanguage, session.identityFingerprint)
        }
        if (!checks.optBoolean("hashtags")) hashtags(session)
    }

    private fun warnDuplicates(session: PipelineSession, consistency: JSONObject) {
        val groups = session.images.groupBy { image ->
            "${image.width}x${image.height}:${image.compressedBytes / 8_000L}"
        }.values.filter { it.size > 1 }
        val remote = consistency.optJSONArray("duplicate_groups")
        val remoteCount = if (remote == null) 0 else {
            (0 until remote.length()).count { remote.optJSONArray(it)?.length() ?: 0 > 1 }
        }
        if (groups.isNotEmpty() || remoteCount > 0) {
            session.warnings.add("Repeated images grouped — extras are treated as duplicates.")
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
