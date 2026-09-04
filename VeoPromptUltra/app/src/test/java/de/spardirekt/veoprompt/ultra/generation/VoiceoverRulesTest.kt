package de.spardirekt.veoprompt.ultra.generation

import de.spardirekt.veoprompt.ultra.model.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceoverRulesTest {
    @Test
    fun germanPreferredRange() {
        val ok = "Tiefer Topf, fester Holzdeckel, einfach kochen."
        assertEquals(6, VoiceoverRules.wordCount(ok))
        assertTrue(VoiceoverRules.issues(ok, VoiceLanguage.DE).any { it.startsWith("voiceover_word_count") })
        val good = "Tiefer Topf mit festem Holzdeckel hält die Hitze lange und sicher."
        assertTrue(VoiceoverRules.wordCount(good) in 10..16)
        assertTrue(VoiceoverRules.issues(good, VoiceLanguage.DE).isEmpty())
    }

    @Test
    fun offHasNoIssues() {
        assertTrue(VoiceoverRules.issues("anything", VoiceLanguage.OFF).isEmpty())
    }
}
