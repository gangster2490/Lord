package de.spardirekt.agents.pro.generation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCorePromptTest {

    @Test
    fun coreContainsProductFidelityAndEightSecondLaw() {
        val core = AgentCorePrompt.CORE
        assertTrue(core.contains("PRODUCT DESIGN = LOCKED"))
        assertTrue(core.contains("exactly 8.0 seconds") || core.contains("exactly 8-second"))
        assertTrue(core.contains("0.0–2.0s"))
        assertTrue(core.contains("6.0–8.0s"))
        assertTrue(core.contains("HASHTAGS"))
        assertTrue(core.contains("EXACTLY 5"))
        assertTrue(core.contains("NO Primary Reference") || core.contains("No Primary Reference") || core.contains("NO Primary"))
        assertTrue(core.contains("marketplace screenshots are reference material only"))
        assertTrue(core.contains("TIKTOK SHOP SAFETY AUDIT"))
        assertTrue(core.contains("must end at HASHTAGS") || core.contains("must end at HASHTAGS".lowercase()) || core.contains("end at HASHTAGS"))
        assertTrue(core.contains("Fishing chair"))
        assertTrue(core.contains("closed case"))
        assertTrue(core.contains("Do not invent an open burner") || core.contains("MUST NOT invent an open burner"))
    }

    @Test
    fun everyStagePromptEmbedsTheCore() {
        val stages = listOf(
            PromptTemplates.photoAnalysisSystem(),
            PromptTemplates.productModelSystem(),
            PromptTemplates.creativeDirectorSystem(),
            PromptTemplates.finalPromptSystem("DE", true),
            PromptTemplates.targetedRepairSystem(listOf("VOICEOVER"))
        )
        stages.forEach { prompt ->
            assertTrue(prompt.startsWith(AgentCorePrompt.CORE) || prompt.contains("YOU ARE THE INTERNAL AI AGENT OF VEO PROMPT PRO"))
            assertTrue(prompt.contains("CURRENT STAGE CONTRACT") || prompt.contains("CURRENT STAGE"))
            assertFalse(prompt.contains("Primary Reference selection"))
        }
    }

    @Test
    fun finalPromptStageHasNoLegacyCompositionBudget() {
        val contract = PromptTemplates.finalPromptSystem("DE", true)
            .substringAfter("CURRENT STAGE: FINAL_PROMPT")

        assertFalse(contract.contains("under ~1200"))
        assertFalse(contract.contains("5–6 short bullets"))
        assertFalse(contract.contains("ONE line of 5–8"))
        assertFalse(contract.contains("keep the copied prompt SHORT", ignoreCase = true))
        assertTrue(contract.contains("approximately 5–12"))
        assertTrue(contract.contains("approximately 8–15"))
        assertTrue(contract.contains("There is no character target or prompt-length budget."))
        assertTrue(contract.contains("never emit an ellipsis caused by truncation"))
        assertTrue(contract.contains("Never name it mainPrompt"))
        assertTrue(contract.contains("Forbidden headings inside veoPrompt"))
    }
}
