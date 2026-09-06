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
    fun kitchenRussianHookIsWarmAndHomely() {
        val analysis = JSONObject().put("observed_use_case", "microwave cover").put("product_category", "kitchen")
        val hook = HookEngine.generate(analysis, "РУССКИЙ")
        val options = HookEngine.candidates(analysis, "РУССКИЙ")
        assertEquals(3, options.size)
        assertTrue(options.contains(hook))
        assertFalse(HookEngine.isWeak(hook, "РУССКИЙ"))
        assertTrue(HookEngine.isWarm(hook, "РУССКИЙ"))
        assertTrue(
            hook.contains("просто и удобно") ||
                hook.contains("приятно иметь дома") ||
                hook.contains("уютн") ||
                hook.contains("Люблю"),
        )
        assertTrue(HookEngine.qualityScore(hook, "РУССКИЙ") >= 0.7)
    }

    @Test
    fun germanKitchenHookIsWarmNotPresenter() {
        val analysis = JSONObject().put("observed_use_case", "cover food").put("product_category", "kitchen")
        val hook = HookEngine.generate(analysis, "DEUTSCH")
        assertFalse(HookEngine.isWeak(hook, "DEUTSCH"))
        assertTrue(HookEngine.isWarm(hook, "DEUTSCH"))
        assertFalse(hook.contains("Produktpresenter", ignoreCase = true))
        assertTrue(hook.contains("Küche") || hook.contains("Hause") || hook.contains("gemütlich") || hook.contains("mag"))
        val options = HookEngine.candidates(analysis, "DEUTSCH")
        assertEquals(3, options.size)
        assertTrue(options.contains(hook))
    }

    @Test
    fun rejectsUnsupportedCommercialHook() {
        assertTrue(HookEngine.isWeak("Buy now — this product is guaranteed 100% perfect.", "DEUTSCH"))
        assertTrue(HookEngine.isWeak("Купите сейчас, данное изделие сохраняет влагу.", "РУССКИЙ"))
    }
}
