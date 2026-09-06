package de.spardirekt.ugcclean.gen

import com.google.common.truth.Truth.assertThat
import de.spardirekt.ugcclean.model.SpeechLanguage
import org.junit.Test

class CrossProductGuardTest {
    @Test
    fun chairPrompt_doesNotLeakKitchenOrMicrowaveOrFishing() {
        val prompt = PromptComposer.compose(Fixtures.chair, SpeechLanguage.DE).lowercase()
        listOf("microwave", "mikrowelle", "reservoir", "fishing", "angel", "pfanne", "pan lid").forEach { leak ->
            assertThat(prompt).doesNotContain(leak)
        }
        assertThat(prompt).contains("armchair")
        assertThat(prompt).contains("living room")
    }

    @Test
    fun organizerPrompt_doesNotStealKitchenDesire() {
        val prompt = PromptComposer.compose(Fixtures.organizer, SpeechLanguage.DE).lowercase()
        listOf("kitchen", "küche", "microwave", "fishing", "lake").forEach { leak ->
            assertThat(prompt).doesNotContain(leak)
        }
        assertThat(prompt).contains("desk")
    }

    @Test
    fun panPrompt_keepsKitchenContextWithoutMicrowaveCoverLanguage() {
        val prompt = PromptComposer.compose(Fixtures.pan, SpeechLanguage.DE).lowercase()
        assertThat(prompt).contains("frying pan")
        assertThat(prompt).doesNotContain("microwave")
        assertThat(prompt).doesNotContain("splatter")
        assertThat(prompt).doesNotContain("armchair")
    }
}
