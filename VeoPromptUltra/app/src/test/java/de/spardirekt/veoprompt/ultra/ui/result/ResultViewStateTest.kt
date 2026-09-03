package de.spardirekt.veoprompt.ultra.ui.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultViewStateTest {
    @Test
    fun previewDoesNotChangeStoredPrompt() {
        val full = "A".repeat(400) + "\nEND"
        val state = ResultViewState(storedVeoPrompt = full, veoPrompt = full, expanded = false)
        val preview = state.preview()
        assertTrue(preview.length < full.length)
        assertEquals(full, state.storedVeoPrompt)
        assertFalse(state.storedVeoPrompt.endsWith("..."))
    }

    @Test
    fun packageListsFieldsThenGeminiCopy() {
        val state = ResultViewState(
            veoPrompt = "PROMPT",
            voiceover = "VO",
            title = "Title",
            hashtags = listOf("#a", "#b", "#c", "#d", "#e"),
            language = "DE"
        )
        val pack = state.packageText()
        assertTrue(pack.contains("Озвучка (DE): VO"))
        assertTrue(pack.contains("Название: Title"))
        assertTrue(pack.contains("#a"))
        assertTrue(pack.contains("VEO 3.1 PROMPT"))
        assertTrue(pack.contains("PROMPT"))
    }

    @Test
    fun copyNoticeDoesNotChangeStoredPrompt() {
        val full = "FORMAT\nVertical 9:16.\n" + "X".repeat(300)
        val state = ResultViewState(
            storedVeoPrompt = full,
            veoPrompt = "FORMAT\nVertical 9:16. Photorealistic TikTok Shop ad. Exactly 8.0s.\n",
            copyNotice = "VEO Prompt скопирован"
        )
        assertEquals(full, state.storedVeoPrompt)
        assertEquals("VEO Prompt скопирован", state.copyNotice)
        assertTrue(state.preview().length < full.length || state.veoPrompt.length < full.length)
    }

    @Test
    fun aigcChecklistCopyStaysOutsideVeoPrompt() {
        val prompt = "FORMAT\nVertical 9:16.\nPRODUCT LOCK\nKeep the pan."
        val state = ResultViewState(
            storedVeoPrompt = prompt,
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
        assertEquals(prompt, state.storedVeoPrompt)
        assertFalse(state.packageText().contains("AIGC_DISCLOSE"))
    }

    @Test
    fun geminiChecklistCopyStaysOutsideVeoPrompt() {
        val prompt = "FORMAT\nVertical 9:16.\nPRODUCT LOCK\nKeep the pan."
        val state = ResultViewState(
            storedVeoPrompt = prompt,
            veoPrompt = prompt,
            geminiVerdict = "READY",
            geminiPolicyVersion = "2026.09-v1",
            geminiChecklist = listOf("OK · GV_SUBMIT · Paste full prompt"),
            geminiPublishSteps = listOf("Copy the full veoPrompt into Gemini / VEO")
        )
        val text = state.geminiChecklistText()
        assertTrue(text.contains("READY"))
        assertTrue(text.contains("GV_SUBMIT"))
        assertTrue(text.contains("Copy the full veoPrompt"))
        assertEquals(prompt, state.storedVeoPrompt)
        assertFalse(state.packageText().contains("GV_SUBMIT"))
        assertFalse(state.packageText().contains("GEMINI AUDIT"))
    }
}
