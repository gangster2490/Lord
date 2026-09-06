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
        assertEquals("Вот такую вещь приятно иметь дома.", hook)
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
    }
}
