package de.spardirekt.ugcagent.v3.prompt

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptsTest {
    @Test
    fun v3PromptsAreNotV1Style() {
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("REFERENCE IMAGE OVERRIDES"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("No spoken dialogue"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("Do not overuse"))
        assertTrue(SystemPrompts.PRODUCT_ANALYSIS.contains("text_claims"))
        assertTrue(SystemPrompts.CONSISTENCY.contains("same_product"))
    }
}
