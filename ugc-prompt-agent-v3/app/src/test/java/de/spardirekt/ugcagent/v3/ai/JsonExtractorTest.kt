package de.spardirekt.ugcagent.v3.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExtractorTest {
    @Test
    fun stripsMarkdownFences() {
        val obj = JsonExtractor.extractObject("```json\n{\"same_product\":true,\"confidence\":0.9}\n```")
        assertTrue(obj.getBoolean("same_product"))
        assertEquals(0.9, obj.getDouble("confidence"), 0.001)
    }

    @Test
    fun fillsAnalysisDefaults() {
        val obj = JsonExtractor.withDefaults(JsonExtractor.extractObject("{}"), JsonExtractor.analysisSchemaKeys())
        JsonExtractor.analysisSchemaKeys().forEach { key ->
            assertTrue(obj.has(key))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmpty() {
        JsonExtractor.extractObject("no json here")
    }
}
