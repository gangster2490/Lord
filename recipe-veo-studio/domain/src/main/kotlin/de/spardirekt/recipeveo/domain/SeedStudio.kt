package de.spardirekt.recipeveo.domain

object SeedStudio {
    fun populated(clock: AppClock, prefs: Prefs = Prefs()): StudioState {
        val now = clock.nowMillis()
        val cream = ready(Catalog.cream, ShotStyle.Macro, VoiceLang.DE, now - 30_000, clock)
        val buds = ready(Catalog.buds, ShotStyle.Demo, VoiceLang.DE, now - 20_000, clock)
        val kettle = ready(Catalog.kettle, ShotStyle.Lifestyle, VoiceLang.RU, now - 10_000, clock)
        val draft = Project(
            id = "draft-open",
            photos = listOf(PhotoRef("draft-photo", Catalog.DEMO_CREAM)),
            productId = Catalog.cream.id,
            voice = prefs.defaultVoice,
            tiktokShop = prefs.tiktokShop,
            createdAt = now,
            updatedAt = now,
        )
        return StudioState(
            prefs = prefs,
            projects = listOf(draft, kettle, buds, cream),
            activeId = draft.id,
        )
    }

    private fun ready(
        profile: ProductProfile,
        style: ShotStyle,
        voice: VoiceLang,
        updatedAt: Long,
        clock: AppClock,
    ): Project {
        val photos = listOf(PhotoRef("${profile.id}-photo", profile.demoUri))
        val project = Project(
            id = "seed-${profile.id}",
            title = profile.title,
            photos = photos,
            voice = voice,
            style = style,
            tiktokShop = true,
            productId = profile.id,
            status = ProjectStatus.Ready,
            stage = GenerationStage.DONE,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        )
        return project.copy(result = VeoRecipe.fromProject(project, clock.nowMillis()))
    }
}
