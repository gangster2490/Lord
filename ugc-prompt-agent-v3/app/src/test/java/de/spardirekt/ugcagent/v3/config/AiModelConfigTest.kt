package de.spardirekt.ugcagent.v3.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelConfigTest {
    @Test
    fun openaiOrderIsCentralized() {
        assertEquals("gpt-5.6-sol", AiModelConfig.OPENAI_PRIMARY)
        assertEquals(listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-4o"), AiModelConfig.openaiModels)
    }

    @Test
    fun geminiOrderIsCentralized() {
        assertEquals("gemini-2.5-flash", AiModelConfig.GEMINI_PRIMARY)
        assertTrue(AiModelConfig.geminiModels.contains("gemini-1.5-flash"))
    }

    @Test
    fun filtersToAvailableModelsThenFallsBack() {
        val available = setOf("gpt-4o")
        assertEquals(listOf("gpt-4o"), AiModelConfig.openaiCandidates(available))
        assertEquals(AiModelConfig.openaiModels, AiModelConfig.openaiCandidates(emptySet()))
    }
}
