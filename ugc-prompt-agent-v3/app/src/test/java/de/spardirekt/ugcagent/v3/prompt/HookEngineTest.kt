package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookEngineTest {
    @Test
    fun rejectsDescriptiveRussianHook() {
        assertTrue(HookEngine.isWeak("Ручка удобно расположена сбоку.", "РУССКИЙ"))
    }

    @Test
    fun kitchenRussianHookMatchesMicrowaveProblem() {
        val analysis = JSONObject().put("observed_use_case", "microwave cover").put("product_category", "kitchen")
        val hook = HookEngine.generate(analysis, "РУССКИЙ")
        val options = HookEngine.candidates(analysis, "РУССКИЙ")
        assertEquals(3, options.size)
        assertTrue(options.contains(hook))
        assertFalse(HookEngine.isWeak(hook, "РУССКИЙ", analysis))
        assertTrue(hook.contains("микроволн") || hook.contains("разогрев") || hook.contains("убор") || hook.contains("Надоел"))
        assertTrue(HookEngine.qualityScore(hook, "РУССКИЙ", analysis) >= 0.7)
    }

    @Test
    fun germanKitchenHookIsHumanNotPresenter() {
        val analysis = JSONObject().put("observed_use_case", "cover food").put("product_category", "kitchen")
        val hook = HookEngine.generate(analysis, "DEUTSCH")
        assertFalse(HookEngine.isWeak(hook, "DEUTSCH", analysis))
        assertFalse(hook.contains("Produktpresenter", ignoreCase = true))
        assertTrue(hook.contains("Mikrowelle") || hook.contains("Küche") || hook.contains("Aufwärm") || hook.contains("putzen") || hook.contains("mag"))
        val options = HookEngine.candidates(analysis, "DEUTSCH")
        assertEquals(3, options.size)
        assertTrue(options.contains(hook))
    }

    @Test
    fun panHookStaysWarmAndHomely() {
        val analysis = JSONObject().put("observed_use_case", "frying pan").put("product_category", "kitchen")
        val hook = HookEngine.generate(analysis, "РУССКИЙ", ProductIdentity.cookwarePanFingerprint())
        assertTrue(HookEngine.isWarm(hook, "РУССКИЙ") || hook.contains("кухн") || hook.contains("посуд"))
        assertFalse(HookEngine.isWeak(hook, "РУССКИЙ", analysis))
    }

    @Test
    fun rejectsUnsupportedCommercialHook() {
        assertTrue(HookEngine.isWeak("Buy now — this product is guaranteed 100% perfect.", "DEUTSCH"))
        assertTrue(HookEngine.isWeak("Купите сейчас, данное изделие сохраняет влагу.", "РУССКИЙ"))
    }
}
