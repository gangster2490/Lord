package de.spardirekt.ugcclean.gen

import com.google.common.truth.Truth.assertThat
import de.spardirekt.ugcclean.model.ProductPlan
import de.spardirekt.ugcclean.model.SpeechLanguage
import org.junit.Test

class PromptComposerTest {
    @Test
    fun compose_emitsEachCanonicalHeadingOnce() {
        val prompt = PromptComposer.compose(Fixtures.chair, SpeechLanguage.DE)
        val counts = PromptComposer.headingCounts(prompt)
        PromptComposer.HEADINGS.forEach { heading ->
            assertThat(counts[heading]).isEqualTo(1)
        }
        assertThat(PromptComposer.isCanonical(prompt)).isTrue()
        assertThat(prompt.trim().endsWith("transition tail.")).isTrue()
    }

    @Test
    fun compose_usesRussianHookWhenLanguageIsRu() {
        val prompt = PromptComposer.compose(Fixtures.chair, SpeechLanguage.RU)
        assertThat(prompt).contains("Смотри, эта полка")
        assertThat(prompt).doesNotContain("Schau mal, die Pfanne")
    }

    @Test
    fun compose_locksFirstFrameAndEightSeconds() {
        val prompt = PromptComposer.compose(Fixtures.pan, SpeechLanguage.DE)
        assertThat(prompt).contains("9:16")
        assertThat(prompt).contains("8.0")
        assertThat(prompt).contains("First Frame")
        assertThat(prompt).contains("Target generator: Veo")
        assertThat(prompt).contains("One video = one desire: cook evenly without sticking")
        assertThat(prompt).contains("A similar product from the same category is a failed generation")
        assertThat(prompt).contains("same number of distinct components")
    }

    @Test
    fun compose_doesNotDropDesireOrAllowSameCategoryLookalike() {
        val prompt = PromptComposer.compose(Fixtures.organizer, SpeechLanguage.DE)
        assertThat(prompt).contains("One video = one desire: clear the desk in one place")
        assertThat(prompt).contains("Visible identity-critical facts: white compartments")
        assertThat(prompt).doesNotContain("similar product from the same category is acceptable")
        assertThat(PromptComposer.isCanonical(prompt)).isTrue()
    }
}

object Fixtures {
    val chair = ProductPlan(
        productName = "beige living-room armchair",
        category = "living",
        visibleFeatures = listOf("beige fabric", "wooden legs", "rounded backrest", "single seat", "no wheels"),
        movingParts = listOf("legs", "backrest"),
        useCase = "sit and read in the living room",
        desire = "a calm place to sit at home",
        setting = "a lived-in living room with a sofa nearby",
        camera = "slight handheld from the side",
        action = "a person sits down and settles with a book",
        humanBehaviour = "quiet, unhurried",
        lighting = "afternoon window light",
        spokenHookDe = "Endlich ein Platz, der wirklich hält.",
        spokenHookRu = "Смотри, эта полка сразу наводит порядок.",
    )

    val pan = ProductPlan(
        productName = "deep black frying pan",
        category = "kitchen",
        visibleFeatures = listOf("deep black body", "long handle", "helper handle", "no lid"),
        movingParts = listOf("handle"),
        useCase = "fry on a home stove",
        desire = "cook evenly without sticking",
        setting = "a normal home kitchen with a stove",
        camera = "handheld over the stove",
        action = "hand tilts the pan slightly then sets it down",
        humanBehaviour = "casual cooking glance",
        lighting = "kitchen ceiling light",
        spokenHookDe = "Schau mal, die Pfanne wird einfach heiß.",
        spokenHookRu = "Смотри, сковорода сразу берёт жар.",
    )

    val organizer = ProductPlan(
        productName = "white desk organizer",
        category = "office",
        visibleFeatures = listOf("white compartments", "upright slots", "desktop footprint"),
        movingParts = listOf("none"),
        useCase = "sort pens on a desk",
        desire = "clear the desk in one place",
        setting = "a small home office desk",
        camera = "handheld across the desk",
        action = "a hand drops pens into a slot",
        humanBehaviour = "quick tidy motion",
        lighting = "desk lamp",
        spokenHookDe = "So bleibt der Schreibtisch frei.",
        spokenHookRu = "Смотри, стол сразу чистый.",
    )
}
