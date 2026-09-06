package de.spardirekt.ugcclean.gen

import com.google.common.truth.Truth.assertThat
import de.spardirekt.ugcclean.model.SpeechLanguage
import org.junit.Test

class StartGateTest {
    @Test
    fun canStart_requiresThreePhotosAndKey() {
        assertThat(StartGate.canStart(2, true, false)).isFalse()
        assertThat(StartGate.canStart(3, false, false)).isFalse()
        assertThat(StartGate.canStart(3, true, true)).isFalse()
        assertThat(StartGate.canStart(3, true, false)).isTrue()
        assertThat(StartGate.canStart(15, true, false)).isTrue()
        assertThat(StartGate.canStart(16, true, false)).isFalse()
    }

    @Test
    fun blockReason_mentionsMissingLanguage() {
        val reason = StartGate.blockReason(3, true, false, null)
        assertThat(reason).contains("Sprache")
        assertThat(StartGate.blockReason(3, true, false, SpeechLanguage.DE)).isNull()
    }
}
