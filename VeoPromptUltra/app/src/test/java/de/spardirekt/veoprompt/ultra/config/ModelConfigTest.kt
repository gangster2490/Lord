package de.spardirekt.veoprompt.ultra.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigTest {
    @Test
    fun unknownFallsBackToBalanced() {
        assertEquals("gpt-4o", ModelConfig.sanitize("gpt-invented-99"))
        assertEquals(ModelConfig.Profile.BALANCED, ModelConfig.profile(null))
    }

    @Test
    fun knownIdsStay() {
        assertEquals("gpt-4.1", ModelConfig.sanitize("gpt-4.1"))
        assertEquals("gpt-4o-mini", ModelConfig.sanitize("gpt-4o-mini"))
        assertTrue(ModelConfig.ids.containsAll(listOf("gpt-4o", "gpt-4.1", "gpt-4o-mini")))
    }

    @Test
    fun labelsAreFixed() {
        assertEquals("Balanced", ModelConfig.Profile.BALANCED.label)
        assertEquals("Best Quality", ModelConfig.Profile.BEST_QUALITY.label)
        assertEquals("Economy", ModelConfig.Profile.ECONOMY.label)
    }
}
