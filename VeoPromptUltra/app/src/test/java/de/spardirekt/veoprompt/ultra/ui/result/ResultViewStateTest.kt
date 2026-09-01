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
}
