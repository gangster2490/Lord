package de.spardirekt.ugcagent.prompt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PromptSanitizerTest {

    @Test
    fun stripsMarkdownFencesAndQuotes() {
        val raw = "```text\n\"Handheld, Fensterlicht, Hand greift ins Bild.\"\n```"
        assertThat(PromptSanitizer.clean(raw))
            .isEqualTo("Handheld, Fensterlicht, Hand greift ins Bild.")
    }

    @Test
    fun capsAtEightyWords() {
        val raw = (1..120).joinToString(" ") { "wort$it" }
        val cleaned = PromptSanitizer.clean(raw)
        assertThat(PromptSanitizer.wordCount(cleaned)).isEqualTo(PromptSanitizer.MAX_WORDS)
        val evaluated = PromptSanitizer.evaluate(raw)
        assertThat(evaluated.truncated).isTrue()
        assertThat(evaluated.wordCount).isEqualTo(80)
    }

    @Test
    fun flagsVisualProductLeaksWithoutRewritingIdentity() {
        val leak = "Die Form und Farbe des Produkts bleiben identisch, Material Metall."
        assertThat(PromptSanitizer.hasVisualProductLeak(leak)).isTrue()
        val cleanUgc = "Handyaufnahme, leicht verzittert, Küche, jemand greift zu und lacht kurz."
        assertThat(PromptSanitizer.hasVisualProductLeak(cleanUgc)).isFalse()
        assertThat(PromptSanitizer.clean(leak)).isEqualTo(leak)
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertThat(PromptSanitizer.clean("   ")).isEmpty()
        assertThat(PromptSanitizer.wordCount("")).isEqualTo(0)
    }
}
