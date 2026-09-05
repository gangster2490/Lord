package de.spardirekt.ugcagent.v3.prompt

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptsTest {
    @Test
    fun v3PromptsAreNotV1Style() {
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("REFERENCE IMAGE OVERRIDES"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("No spoken dialogue"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("Do not overuse"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("FINAL IDENTITY LOCK"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("exactly 8.0 seconds"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("MOVING COMPONENT LOCK"))
        assertTrue(SystemPrompts.VIDEO_PROMPT.contains("freeze-frame tail"))
        assertTrue(SystemPrompts.PRODUCT_ANALYSIS.contains("text_claims"))
        assertTrue(SystemPrompts.CONSISTENCY.contains("same_product"))
        assertTrue(SystemPrompts.CONSISTENCY.contains("duplicate_groups"))
        assertTrue(SystemPrompts.CONSISTENCY.contains("hard_geometry_conflict"))
        assertTrue(SystemPrompts.CONSISTENCY.contains("identity-critical visible geometry"))
        assertTrue(SystemPrompts.CONSISTENCY.contains("packaging"))
        assertTrue(SystemPrompts.PRODUCT_IDENTITY_FINGERPRINT.contains("identity_critical_components"))
    }
}
