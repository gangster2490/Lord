package de.spardirekt.recipeveo.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecipeCompilerTest {
    private val clock = FixedAppClock(99L)

    @Test
    fun compiledPromptContainsAllTwelveSectionsAndEightSeconds() {
        val recipe = SeedLibrary.populated(clock).recipes.first()
        val compiled = RecipeCompiler.compile(recipe, clock)
        assertThat(RecipeCompiler.looksComplete(compiled.veoPrompt)).isTrue()
        assertThat(compiled.veoPrompt).contains("Exactly 8.0s")
        assertThat(compiled.veoPrompt).contains("0.0–2.0s")
        assertThat(compiled.veoPrompt).contains("6.0–8.0s")
        assertThat(compiled.hashtags).hasSize(5)
        assertThat(compiled.compiledAt).isEqualTo(99L)
    }

    @Test
    fun voiceOffWritesOffAndEmptySpokenLine() {
        val recipe = SeedLibrary.populated(clock).recipes.first { it.voice == VoiceLang.OFF }
        val compiled = RecipeCompiler.compile(recipe, clock)
        assertThat(compiled.voiceover).isEmpty()
        assertThat(compiled.veoPrompt).contains("VOICEOVER\nOFF")
    }

    @Test
    fun germanVoiceoverStaysGerman() {
        val recipe = SeedLibrary.populated(clock).recipes.first { it.voice == VoiceLang.DE }
        val compiled = RecipeCompiler.compile(recipe, clock)
        assertThat(compiled.voiceover).contains("bleibt sichtbar")
    }

    @Test
    fun blankActionsAreFilledFromKindFallbacks() {
        val recipe = Recipe(
            id = "x",
            title = "Тест",
            subject = "copper pan",
            kind = RecipeKind.PRODUCT,
            style = VisualStyle.STUDIO,
            setting = "",
            lockNotes = emptyList(),
            beats = defaultBeats(),
            voice = VoiceLang.RU,
            createdAt = 1,
            updatedAt = 1,
        )
        val compiled = RecipeCompiler.compile(recipe, clock)
        assertThat(compiled.veoPrompt).contains("copper pan")
        assertThat(compiled.veoPrompt).contains("Uncluttered premium studio")
        RecipeCompiler.sectionOrder.forEach { heading ->
            assertThat(compiled.veoPrompt).contains(heading)
        }
    }

    @Test
    fun sharePackageKeepsPromptAndHashtags() {
        val compiled = RecipeCompiler.compile(SeedLibrary.populated(clock).recipes.first(), clock)
        val share = RecipeCompiler.sharePackage(compiled)
        assertThat(share).contains("FORMAT")
        assertThat(share).contains(compiled.title)
        assertThat(share).contains(compiled.hashtags.first())
    }
}

class StudioStateTest {
    private val clock = FixedAppClock(10L)

    @Test
    fun compileWritesBundleOntoTheRecipe() {
        val seeded = SeedLibrary.populated(clock)
        val id = seeded.recipes.first().id
        val next = seeded.upsert(
            seeded.recipes.first().copy(compiled = null, title = "Новая буррата"),
        ).compile(id, clock)
        val recipe = next.recipe(id)!!
        assertThat(recipe.compiled).isNotNull()
        assertThat(recipe.compiled!!.title).isEqualTo("Новая буррата")
        assertThat(recipe.updatedAt).isEqualTo(10L)
    }

    @Test
    fun filterByKindAndQuery() {
        val state = SeedLibrary.populated(clock)
        assertThat(state.visible("буррата", null)).hasSize(1)
        assertThat(state.visible("", RecipeKind.DRINK).map { it.title }).containsExactly("Эспрессо в чашке")
        assertThat(state.visible("нет такого", null)).isEmpty()
    }

    @Test
    fun deleteRemovesRecipe() {
        val state = SeedLibrary.populated(clock)
        val id = state.recipes.first().id
        assertThat(state.delete(id).recipes.map { it.id }).doesNotContain(id)
    }
}
