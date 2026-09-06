package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseAppealEngineTest {
    @Test
    fun fishingChairPicksOneOutdoorDesireNotATechnicalDemo() {
        val analysis = JSONObject()
            .put("product_category", "outdoor")
            .put("observed_use_case", "fishing chair")
            .put("observed_context", "lakeside")
        val fingerprint = CreativeStrategyEngine.fishingChairFingerprint()
        val brief = PurchaseAppealEngine.evaluate(analysis, fingerprint)
        assertEquals(CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY, brief.plan.primary)
        assertEquals(CreativeStrategyEngine.Motivation.COMFORT, brief.plan.secondary)
        assertEquals(3, brief.concepts.size)
        assertTrue(brief.concepts.map { it.kind }.containsAll(listOf("safest", "purchase", "natural")))
        assertFalse(brief.toPublicJson().toString().contains("safest concept"))
        assertTrue(brief.plan.hookType == CreativeStrategyEngine.HookType.OUTDOOR || brief.plan.hookType == CreativeStrategyEngine.HookType.CONVENIENCE)
        assertTrue(brief.setting.contains("lakeside") || brief.setting.contains("riverside"))
        assertTrue(brief.action.contains("already seated"))
        assertTrue(PurchaseAppealEngine.scoreAction("fold the chair and rotate the backrest 180 degrees").highRisk)
        assertFalse(PurchaseAppealEngine.scoreAction(brief.action).highRisk)
        val hook = HookEngine.generate(analysis, "РУССКИЙ", fingerprint, brief.plan)
        val candidates = HookEngine.candidates(analysis, "РУССКИЙ", fingerprint, brief.plan)
        assertTrue(candidates.contains("Вот так на рыбалке сидеть уже совсем другое дело."))
        assertTrue(candidates.contains("Когда на рыбалке всё удобно — и отдых совсем другой."))
        assertTrue(hook.contains("рыбал") || hook.contains("берег") || hook.contains("отдых"))
        val prompt = ProductLock.finalizeClean(
            "ACTION:\nfold the chair and rotate the backrest 180 degrees.",
            fingerprint,
            "VEO",
            "РУССКИЙ",
            hook,
            true,
            analysis,
        )
        assertTrue(
            CreativeStrategyEngine.gateFailures(prompt, hook, brief.plan, "РУССКИЙ", analysis).isEmpty(),
        )
        assertTrue(prompt.contains("One desire only"))
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        assertTrue(prompt.substringAfter("FORMAT:").contains("not a technical demo", ignoreCase = true))
    }

    @Test
    fun microwaveKeepsProblemDesireAndKitchenIdentity() {
        val analysis = JSONObject().put("product_category", "kitchen").put("observed_use_case", "cover food")
        val brief = PurchaseAppealEngine.evaluate(analysis, ProductIdentity.microwaveCoverFingerprint())
        assertEquals(CreativeStrategyEngine.Motivation.PROBLEM_SOLVER, brief.plan.primary)
        assertEquals(CreativeStrategyEngine.SellingIdea.CLEANLINESS, brief.plan.idea)
        assertEquals(CreativeStrategyEngine.HookType.PROBLEM, brief.plan.hookType)
        val prompt = ProductLock.finalizeClean(
            "ACTION:\none hand grips the handle.",
            ProductIdentity.microwaveCoverFingerprint(),
            "VEO",
            "DEUTSCH",
            null,
            true,
            analysis,
        )
        assertTrue(ProductLock.preservesMicrowaveCover(prompt))
        assertTrue(prompt.contains("Warm, homely"))
        assertTrue(prompt.contains("One desire only: cleanliness") || prompt.contains("cleanliness"))
    }

    @Test
    fun unknownCategoryKeepsInternalConceptsOffThePublicBrief() {
        val analysis = JSONObject().put("product_category", "unknown gadget")
        val brief = PurchaseAppealEngine.evaluate(analysis)
        assertEquals(3, brief.concepts.size)
        assertTrue(brief.concepts.map { it.kind }.containsAll(listOf("safest", "purchase", "natural")))
        val json = brief.toPublicJson().toString()
        assertFalse(json.contains("safest concept"))
        assertTrue(json.contains("setting_type"))
        assertEquals(CreativeStrategyEngine.SettingType.INDOOR_NEUTRAL, brief.plan.settingType)
    }
}
