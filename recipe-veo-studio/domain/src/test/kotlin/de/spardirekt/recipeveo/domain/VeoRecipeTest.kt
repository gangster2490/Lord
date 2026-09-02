package de.spardirekt.recipeveo.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VeoRecipeTest {
    private val generic = VeoRecipe.GenerateInput(
        photoCount = 2,
        wish = "",
        voice = VoiceLang.DE,
        style = ShotStyle.Auto,
        tiktokShop = true,
    )

    @Test
    fun twelveSectionsAndFiveHashtags() {
        val pkg = VeoRecipe.compile(generic, 1L)
        assertThat(StudioRules.looksComplete(pkg.veoPrompt, pkg.hashtags)).isTrue()
        assertThat(pkg.hashtags).hasSize(5)
        assertThat(pkg.veoPrompt).contains("0.0–2.0s")
        assertThat(pkg.veoPrompt).contains("6.0–8.0s")
        assertThat(pkg.compiledAt).isEqualTo(1L)
    }

    @Test
    fun genericPhotosDoNotLockCatalogProducts() {
        val pkg = VeoRecipe.compile(generic, 1L)
        assertThat(pkg.title).isEqualTo("Product film")
        assertThat(pkg.veoPrompt).contains("Preserve silhouette")
        assertThat(pkg.veoPrompt).doesNotContain("gold cap")
        assertThat(pkg.veoPrompt).doesNotContain("graphite charging case")
        assertThat(pkg.voiceover).contains("Das Produkt bleibt")
    }

    @Test
    fun catalogProfileLocksNamedProduct() {
        val pkg = VeoRecipe.compile(generic.copy(photoCount = 1, profile = Catalog.cream), 1L)
        assertThat(pkg.title).isEqualTo("Velvet Gold Night Cream")
        assertThat(pkg.veoPrompt).contains("gold cap, ivory jar")
        assertThat(pkg.voiceover).contains("Velvet Gold Night Cream")
        assertThat(pkg.voiceover).contains("TikTok Shop")
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesWithoutPhotos() {
        VeoRecipe.compile(generic.copy(photoCount = 0), 1L)
    }

    @Test
    fun russianVoiceAndProductFilmHashtags() {
        val pkg = VeoRecipe.compile(
            generic.copy(voice = VoiceLang.RU, tiktokShop = false, profile = Catalog.kettle),
            1L,
        )
        assertThat(pkg.voiceover).contains("Ember Pour-Over Kettle")
        assertThat(pkg.voiceover).contains("остаётся")
        assertThat(pkg.voiceover).doesNotContain("TikTok Shop")
        assertThat(pkg.veoPrompt).contains("#ProductFilm")
        assertThat(pkg.hashtags).hasSize(5)
    }

    @Test
    fun voiceOffWritesOff() {
        val pkg = VeoRecipe.compile(generic.copy(voice = VoiceLang.OFF), 1L)
        assertThat(pkg.voiceover).isEmpty()
        assertThat(pkg.veoPrompt).contains("VOICEOVER\nOFF")
    }

    @Test
    fun wishBecomesTitleAndEntersReferences() {
        val pkg = VeoRecipe.compile(
            generic.copy(style = ShotStyle.Macro, wish = "keep the gold lid"),
            2L,
        )
        assertThat(pkg.title).isEqualTo("Keep the gold lid")
        assertThat(pkg.veoPrompt).contains("Wish: keep the gold lid")
        assertThat(pkg.veoPrompt).contains("extreme close-up")
        assertThat(pkg.hashtags).contains("#Macro")
    }

    @Test
    fun fromProjectUsesCatalogUri() {
        val project = Project(
            id = "p",
            photos = listOf(PhotoRef("1", Catalog.DEMO_BUDS)),
            voice = VoiceLang.DE,
            style = ShotStyle.Demo,
            createdAt = 1,
            updatedAt = 1,
        )
        val pkg = VeoRecipe.fromProject(project, 3L)
        assertThat(pkg.title).isEqualTo("Arc Pulse Earbuds")
        assertThat(pkg.veoPrompt).contains("matte graphite charging case")
    }
}

class HomeMathTest {
    @Test
    fun seededStudioIsAlive() {
        val clock = FixedAppClock()
        val state = SeedStudio.populated(clock)
        val snap = HomeMath.snapshot(state, clock)
        assertThat(snap.readyCount).isEqualTo(3)
        assertThat(snap.draftCount).isEqualTo(1)
        assertThat(snap.featured?.title).isEqualTo("Ember Pour-Over Kettle")
        assertThat(snap.draft?.photos).isNotEmpty()
        assertThat(snap.greeting).isEqualTo("Студия вечером")
        assertThat(StudioRules.canGenerate(snap.draft!!)).isTrue()
    }
}

class StudioStateTest {
    @Test
    fun libraryHidesEmptyDraftsButKeepsPhotoDrafts() {
        val state = SeedStudio.populated(FixedAppClock())
        val ids = state.library().map { it.id }
        assertThat(ids).contains("seed-velvet-gold")
        assertThat(ids).contains("draft-open")
    }

    @Test
    fun ensureDraftReusesOpenDraft() {
        val clock = FixedAppClock()
        val state = SeedStudio.populated(clock)
        val again = state.ensureDraft(clock)
        assertThat(again.activeId).isEqualTo("draft-open")
        assertThat(again.projects.count { it.status == ProjectStatus.Draft }).isEqualTo(1)
    }
}
