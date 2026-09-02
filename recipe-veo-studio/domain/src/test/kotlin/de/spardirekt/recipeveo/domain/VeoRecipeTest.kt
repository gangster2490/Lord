package de.spardirekt.recipeveo.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VeoRecipeTest {
    private val base = VeoRecipe.GenerateInput(
        photoCount = 2,
        wish = "",
        voice = VoiceLang.DE,
        creative = CreativeMode.Auto,
        tiktokShop = true,
    )

    @Test
    fun completeTwelveSectionPrompt() {
        val pkg = VeoRecipe.compile(base, 1L)
        assertThat(StudioRules.looksComplete(pkg.veoPrompt, pkg.hashtags)).isTrue()
        assertThat(pkg.veoPrompt).contains("0.0–2.0s")
        assertThat(pkg.veoPrompt).contains("6.0–8.0s")
        assertThat(pkg.voiceover).contains("TikTok Shop")
        assertThat(pkg.compiledAt).isEqualTo(1L)
        assertThat(pkg.hashtags).hasSize(5)
    }

    @Test
    fun userPhotosDoNotLockVelvetGold() {
        val pkg = VeoRecipe.compile(base, 1L)
        assertThat(pkg.veoPrompt).contains("Preserve silhouette")
        assertThat(pkg.veoPrompt).doesNotContain("gold cap")
        assertThat(pkg.veoPrompt).doesNotContain("ivory jar")
        assertThat(pkg.title).isEqualTo("Product film")
        assertThat(pkg.voiceover).contains("originalgetreu")
    }

    @Test
    fun demoProductKeepsVelvetGoldLock() {
        val pkg = VeoRecipe.compile(base.copy(photoCount = 1, demoProduct = true), 1L)
        assertThat(pkg.veoPrompt).contains("gold cap, ivory jar")
        assertThat(pkg.title).isEqualTo("Velvet Gold Night Cream")
        assertThat(pkg.voiceover).contains("goldene Deckel")
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesWithoutPhotos() {
        VeoRecipe.compile(base.copy(photoCount = 0), 1L)
    }

    @Test
    fun russianVoiceoverAndNoShopWhenDisabled() {
        val pkg = VeoRecipe.compile(base.copy(voice = VoiceLang.RU, tiktokShop = false, demoProduct = true), 1L)
        assertThat(pkg.voiceover).contains("Золотая крышка")
        assertThat(pkg.voiceover).doesNotContain("TikTok Shop")
        assertThat(pkg.veoPrompt).contains("#ProductFilm")
        assertThat(pkg.hashtags).hasSize(5)
    }

    @Test
    fun genericRussianVoiceoverWithoutDemo() {
        val pkg = VeoRecipe.compile(base.copy(voice = VoiceLang.RU, tiktokShop = false), 1L)
        assertThat(pkg.voiceover).contains("Товар остаётся")
        assertThat(pkg.voiceover).doesNotContain("Золотая крышка")
    }

    @Test
    fun voiceOffWritesOff() {
        val pkg = VeoRecipe.compile(base.copy(voice = VoiceLang.OFF), 1L)
        assertThat(pkg.voiceover).isEmpty()
        assertThat(pkg.veoPrompt).contains("VOICEOVER\nOFF")
    }

    @Test
    fun macroModeLocksMacroShotsAndWishEntersReferences() {
        val pkg = VeoRecipe.compile(
            base.copy(creative = CreativeMode.Macro, wish = "keep the gold lid"),
            2L,
        )
        assertThat(pkg.veoPrompt).contains("extreme close-up")
        assertThat(pkg.veoPrompt).contains("Wish: keep the gold lid")
        assertThat(pkg.title).isEqualTo("Keep the gold lid")
        assertThat(pkg.hashtags).contains("#Macro")
        assertThat(pkg.hashtags).hasSize(5)
    }
}

class StudioStateTest {
    private val clock = FixedAppClock(10L)

    @Test
    fun generateRequiresPhotos() {
        val state = SeedProjects.populated(clock)
        val draft = state.active()!!
        assertThat(StudioRules.canGenerate(draft)).isFalse()
        val withPhoto = draft.copy(photos = listOf(PhotoRef("1", "content://x")))
        assertThat(StudioRules.canGenerate(withPhoto)).isTrue()
    }

    @Test
    fun historyHidesEmptyDrafts() {
        val state = SeedProjects.populated(clock)
        assertThat(state.history().map { it.id }).containsExactly("seed-velvet-gold")
    }

    @Test
    fun ensureDraftReusesEmptyDraft() {
        val state = SeedProjects.populated(clock)
        val again = state.ensureDraft(clock)
        assertThat(again.activeId).isEqualTo(state.activeId)
        assertThat(again.projects.count { it.status == ProjectStatus.Draft }).isEqualTo(1)
    }
}
