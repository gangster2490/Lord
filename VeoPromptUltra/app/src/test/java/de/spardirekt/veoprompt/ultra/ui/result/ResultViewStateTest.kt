package de.spardirekt.veoprompt.ultra.ui.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultViewStateTest {
    @Test
    fun copyVeoPromptIsUnmodifiedFullString() {
        val full = "A".repeat(400) + "\nEND"
        val state = ResultViewState(veoPrompt = full, expanded = false)
        val preview = state.preview()
        assertTrue(preview.length < full.length)
        assertEquals(full, state.veoPrompt)
        assertFalse(state.veoPrompt.endsWith("..."))
    }

    @Test
    fun packageContainsSeparateFields() {
        val state = ResultViewState(
            veoPrompt = "PROMPT",
            voiceover = "VO",
            title = "Title",
            hashtags = listOf("#a", "#b", "#c", "#d", "#e")
        )
        val pack = state.packageText()
        assertTrue(pack.startsWith("PROMPT"))
        assertTrue(pack.contains("VO"))
        assertTrue(pack.contains("Title"))
        assertTrue(pack.contains("#a"))
    }

    @Test
    fun copyNoticeDoesNotChangeStoredPrompt() {
        val full = "FORMAT\nVertical 9:16.\n" + "X".repeat(300)
        val state = ResultViewState(veoPrompt = full, copyNotice = "VEO Prompt скопирован")
        assertEquals(full, state.veoPrompt)
        assertEquals("VEO Prompt скопирован", state.copyNotice)
        assertTrue(state.preview().length < full.length)
    }

    @Test
    fun aigcChecklistCopyStaysOutsideVeoPrompt() {
        val prompt = "FORMAT\nVertical 9:16.\nPRODUCT LOCK\nKeep the pan."
        val state = ResultViewState(
            veoPrompt = prompt,
            aigcVerdict = "DISCLOSE_REQUIRED",
            aigcPolicyVersion = "2026.05-v1",
            aigcChecklist = listOf("OK · AIGC_DISCLOSE · Label"),
            aigcPublishSteps = listOf("Enable TikTok AI-generated content toggle")
        )
        val text = state.aigcChecklistText()
        assertTrue(text.contains("DISCLOSE_REQUIRED"))
        assertTrue(text.contains("AIGC_DISCLOSE"))
        assertTrue(text.contains("Enable TikTok"))
        assertEquals(prompt, state.veoPrompt)
        assertFalse(state.packageText().contains("AIGC_DISCLOSE"))
    }
}
