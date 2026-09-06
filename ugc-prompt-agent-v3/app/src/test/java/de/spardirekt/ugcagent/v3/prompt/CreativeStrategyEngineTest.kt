package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeStrategyEngineTest {
    @Test
    fun fishingChairUsesOutdoorComfortNotKitchen() {
        val analysis = JSONObject()
            .put("product_category", "outdoor")
            .put("observed_use_case", "fishing chair")
            .put("observed_context", "lakeside")
        val plan = CreativeStrategyEngine.plan(analysis, CreativeStrategyEngine.fishingChairFingerprint())
        assertEquals(CreativeStrategyEngine.Motivation.OUTDOOR_HOBBY, plan.primary)
        assertTrue(plan.hookType == CreativeStrategyEngine.HookType.OUTDOOR || plan.hookType == CreativeStrategyEngine.HookType.CONVENIENCE)
        assertTrue(plan.desire.isNotBlank())
        assertTrue(plan.setting.contains("lakeside") || plan.setting.contains("riverside"))
        assertFalse(plan.setting.contains("kitchen", ignoreCase = true))
        assertTrue(plan.action.contains("already seated"))
        assertFalse(plan.action.contains("fold", ignoreCase = true) && plan.action.startsWith("Fold"))
        assertTrue(plan.human.contains("seated") || plan.human.contains("tray"))
        val hook = HookEngine.generate(analysis, "РУССКИЙ", CreativeStrategyEngine.fishingChairFingerprint(), plan)
        assertTrue(hook.contains("рыбал") || hook.contains("берег"))
        assertFalse(HookEngine.isWeak(hook, "РУССКИЙ", analysis, plan))
        assertFalse(hook.contains("микроволн"))
        val prompt = ProductLock.finalizeClean(
            "ACTION:\nfold the chair and rotate the backrest 180 degrees.",
            CreativeStrategyEngine.fishingChairFingerprint(),
            "VEO",
            "РУССКИЙ",
            hook,
            true,
            analysis,
        )
        assertTrue(prompt.contains("lakeside") || prompt.contains("riverside"))
        assertFalse(prompt.contains("Ordinary cozy home kitchen"))
        assertTrue(prompt.contains("already seated"))
        assertTrue(prompt.contains("Do not fold"))
        assertFalse(prompt.substringAfter("ACTION:").substringBefore("HUMAN").contains("fold the chair and rotate"))
        PromptComposer.CANONICAL_HEADINGS.forEach { heading ->
            assertEquals(heading, 1, PromptComposer.headingCounts(prompt)[heading] ?: 0)
        }
        assertTrue(HookEngine.isWeak("Приятно, когда дома всё просто и по-своему уютно.", "РУССКИЙ", analysis, plan))
        assertTrue(HookEngine.isWeak("Это кресло выдерживает любой вес.", "РУССКИЙ", analysis, plan))
    }

    @Test
    fun microwaveKeepsProblemHookAndKitchenIdentity() {
        val analysis = JSONObject().put("product_category", "kitchen").put("observed_use_case", "cover food")
        val plan = CreativeStrategyEngine.plan(analysis, ProductIdentity.microwaveCoverFingerprint())
        assertEquals(CreativeStrategyEngine.Motivation.PROBLEM_SOLVER, plan.primary)
        assertTrue(plan.setting.contains("kitchen", ignoreCase = true))
        val hook = HookEngine.generate(analysis, "РУССКИЙ", ProductIdentity.microwaveCoverFingerprint(), plan)
        assertTrue(hook.contains("микроволн") || hook.contains("разогрев") || hook.contains("убор"))
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
        assertTrue(prompt.contains("kitchen", ignoreCase = true))
    }

    @Test
    fun panKeepsHomeKitchenHook() {
        val analysis = JSONObject().put("product_category", "kitchen").put("observed_use_case", "frying pan")
        val plan = CreativeStrategyEngine.plan(analysis, ProductIdentity.cookwarePanFingerprint())
        assertEquals(CreativeStrategyEngine.Motivation.HOME_COZY, plan.primary)
        val hook = HookEngine.generate(analysis, "DEUTSCH", ProductIdentity.cookwarePanFingerprint(), plan)
        assertTrue(hook.contains("Küche") || hook.contains("Hause") || hook.contains("Pfanne") || hook.contains("mag"))
        assertTrue(plan.action.contains("wooden handle") || plan.action.contains("lid"))
    }
}
