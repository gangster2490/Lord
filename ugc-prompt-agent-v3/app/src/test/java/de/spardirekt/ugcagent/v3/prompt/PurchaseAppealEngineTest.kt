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
        assertEquals(CreativeStrategyEngine.HookType.OUTDOOR, brief.plan.hookType)
        assertTrue(
            brief.plan.idea == CreativeStrategyEngine.SellingIdea.COMFORT ||
                brief.plan.idea == CreativeStrategyEngine.SellingIdea.OUTDOOR_PRACTICALITY,
        )
        assertEquals(1, brief.concepts.map { it.idea }.distinct().size)
        assertEquals(3, brief.concepts.size)
        assertTrue(brief.concepts.map { it.kind }.containsAll(listOf("safest", "purchase", "natural")))
        assertFalse(brief.toPublicJson().toString().contains("safest concept"))
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

    @Test
    fun travelBagPicksPortabilityNotHomeCozy() {
        val analysis = JSONObject()
            .put("product_category", "travel")
            .put("observed_use_case", "portable folding suitcase")
            .put("observed_context", "airport")
        val brief = PurchaseAppealEngine.evaluate(analysis)
        assertEquals(CreativeStrategyEngine.Motivation.PORTABLE, brief.plan.primary)
        assertEquals(CreativeStrategyEngine.SellingIdea.PORTABILITY, brief.plan.idea)
        assertEquals(1, brief.concepts.map { it.idea }.distinct().size)
        assertFalse(brief.plan.idea == CreativeStrategyEngine.SellingIdea.HOME_FEELING)
        assertFalse(brief.plan.idea == CreativeStrategyEngine.SellingIdea.CLEANLINESS)
    }

    @Test
    fun applyKeepsThePlansSellingIdeaAndOnlyVariesAction() {
        val analysis = JSONObject()
            .put("product_category", "office")
            .put("observed_use_case", "desk organizer")
            .put("observed_context", "desk")
            .put("inferred_use_case", "cover food mess splash putzen")
            .put("possible_scene", "Ordinary cozy home kitchen by the lakeside")
        val fingerprint = JSONObject().put("overall_geometry", "desktop organizer tray")
        val draft = CreativeConsistencyEngine.build(analysis, fingerprint)
        val applied = PurchaseAppealEngine.apply(draft, analysis, fingerprint)
        assertEquals(draft.primary, applied.primary)
        assertEquals(draft.idea, applied.idea)
        assertEquals(draft.hookType, applied.hookType)
        assertEquals(draft.setting, applied.setting)
        assertEquals(CreativeStrategyEngine.Motivation.ORGANIZATION, applied.primary)
        val brief = PurchaseAppealEngine.evaluate(analysis, fingerprint)
        assertEquals(1, brief.concepts.map { it.idea }.distinct().size)
        assertEquals(applied.idea, brief.concepts.first().idea)
        assertEquals(applied.hookType, brief.concepts.first().hookType)
        assertEquals(applied.primary, brief.concepts.first().motivation)
        assertFalse(brief.plan.idea == CreativeStrategyEngine.SellingIdea.CLEANLINESS)
        assertFalse(brief.plan.primary == CreativeStrategyEngine.Motivation.PROBLEM_SOLVER)
    }

    @Test
    fun bathroomDispenserSellsConvenienceNotKitchenCleanliness() {
        val analysis = JSONObject()
            .put("product_category", "personal care")
            .put("observed_use_case", "soap dispenser")
            .put("observed_context", "bathroom")
            .put("possible_scene", "cover food mess splash in a cozy kitchen")
        val fingerprint = JSONObject().put("overall_geometry", "pump bottle with cylindrical body")
        val brief = PurchaseAppealEngine.evaluate(analysis, fingerprint)
        assertEquals(CreativeStrategyEngine.SettingType.BATHROOM, brief.plan.settingType)
        assertEquals(CreativeStrategyEngine.Motivation.CONVENIENCE, brief.plan.primary)
        assertEquals(CreativeStrategyEngine.SellingIdea.CONVENIENCE, brief.plan.idea)
        assertEquals(CreativeStrategyEngine.HookType.CONVENIENCE, brief.plan.hookType)
        assertFalse(brief.plan.idea == CreativeStrategyEngine.SellingIdea.CLEANLINESS)
        assertFalse(brief.plan.idea == CreativeStrategyEngine.SellingIdea.HOME_FEELING)
        assertEquals(1, brief.concepts.map { it.idea }.distinct().size)
        val prompt = ProductLock.finalizeClean(
            "ACTION:\none hand rests near the dispenser.",
            fingerprint,
            "VEO",
            "DEUTSCH",
            null,
            true,
            analysis,
        )
        assertTrue(prompt.contains("bathroom", ignoreCase = true))
        assertTrue(prompt.contains("One desire only: convenience"))
        assertFalse(prompt.contains("One desire only: cleanliness"))
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        val hook = ProductLock.extractSpokenHooks(prompt).first()
        assertFalse(hook.contains("Mikrowelle"))
        assertFalse(hook.contains("putzen"))
        assertFalse(hook.contains("Küche"))
    }
}
