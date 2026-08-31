package de.spardirekt.tiktokshop.data.prompt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SystemPromptTest {

    @Test
    fun containsEightSecondRule() {
        assertThat(SystemPrompt.VALUE).contains("exactly 8 seconds")
        assertThat(SystemPrompt.VALUE).contains("VIDEO LENGTH: Exactly 8 seconds")
    }

    @Test
    fun containsProductLockAndHumanInteraction() {
        assertThat(SystemPrompt.VALUE).contains("locked product reference")
        assertThat(SystemPrompt.VALUE).contains("HUMAN INTERACTION RULES")
        assertThat(SystemPrompt.VALUE).contains("Jetzt unten im Warenkorb")
    }

    @Test
    fun forbidsPricesAndRequiresJson() {
        assertThat(SystemPrompt.VALUE).contains("Keine Preise")
        assertThat(SystemPrompt.VALUE).contains("Antworte NUR mit einem gültigen JSON-Objekt")
    }
}
