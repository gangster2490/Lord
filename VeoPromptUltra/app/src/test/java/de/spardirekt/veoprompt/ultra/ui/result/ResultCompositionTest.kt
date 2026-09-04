package de.spardirekt.veoprompt.ultra.ui.result

import de.spardirekt.veoprompt.ultra.generation.Fixtures
import de.spardirekt.veoprompt.ultra.generation.GeminiVeoPromptCleanup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultCompositionTest {

    @Test
    fun geminiCopyIsTwelveSectionAndLeavesStoredUntouched() {
        val stored = Fixtures.validVeoPrompt() + "\n\nSAFETY AUDIT\nsecret\n"
        val copy = ResultComposition.geminiPrompt(
            storedPrompt = stored,
            voiceover = "Tiefer Topf, fester Holzdeckel.",
            title = "Tiefe Pfanne",
            hashtags = listOf("#Pfanne", "#Kochen", "#Holzdeckel", "#Kitchen", "#TikTokShop"),
            marketplace = false,
            tiktokShopMode = true
        )
        assertTrue(copy.contains("TITLE"))
        assertTrue(copy.contains("HASHTAGS"))
        assertTrue(copy.contains("Tiefe Pfanne"))
        assertFalse(copy.contains("SAFETY AUDIT"))
        assertFalse(copy.contains("GEMINI AUDIT"))
        assertTrue(stored.contains("SAFETY AUDIT"))
        assertTrue(copy.length <= GeminiVeoPromptCleanup.MAX_COPIED_PROMPT_CHARS)
        assertTrue(copy.contains("0.0"))
        assertTrue(copy.contains("8.0"))
    }
}
