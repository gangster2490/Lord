package de.spardirekt.ugcagent.v3.prompt

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookEngineTest {
    @Test
    fun rejectsDescriptiveRussianHook() {
        assertTrue(HookEngine.isWeak("Ручка удобно расположена сбоку.", "РУССКИЙ"))
    }

    @Test
    fun microwaveRussianHookIsStrong() {
        val analysis = JSONObject().put("observed_use_case", "microwave cover").put("product_category", "kitchen")
        val hook = HookEngine.generate(analysis, "РУССКИЙ")
        assertFalse(HookEngine.isWeak(hook, "РУССКИЙ"))
        assertTrue(hook.contains("микроволн") || hook.contains("брызг") || hook.contains("вытир") || hook.contains("Надоело"))
        assertTrue(HookEngine.qualityScore(hook, "РУССКИЙ") >= 0.7)
    }

    @Test
    fun germanHookHasFriction() {
        val analysis = JSONObject().put("observed_use_case", "cover food").put("product_category", "kitchen")
        val hook = HookEngine.generate(analysis, "DEUTSCH")
        assertFalse(HookEngine.isWeak(hook, "DEUTSCH"))
        assertTrue(hook.contains("?") || hook.contains("Wenn") || hook.contains("Deshalb") || hook.contains("Lust"))
    }
}
