package de.spardirekt.recipeveo.domain

object SeedProjects {
    const val DEMO_PHOTO = "demo://velvet-gold"

    fun populated(clock: AppClock): StudioState {
        val now = clock.nowMillis()
        val input = VeoRecipe.GenerateInput(
            photoCount = 1,
            wish = "",
            voice = VoiceLang.DE,
            creative = CreativeMode.Auto,
            tiktokShop = true,
            demoProduct = true,
        )
        val ready = Project(
            id = "seed-velvet-gold",
            title = "Velvet Gold Night Cream",
            photos = listOf(PhotoRef("seed-photo", DEMO_PHOTO)),
            voice = VoiceLang.DE,
            creative = CreativeMode.Auto,
            tiktokShop = true,
            status = ProjectStatus.Ready,
            stage = GenerationStage.DONE,
            result = VeoRecipe.compile(input, now),
            createdAt = now - 1,
            updatedAt = now - 1,
        )
        val draft = Project(
            id = newId(),
            voice = VoiceLang.DE,
            tiktokShop = true,
            createdAt = now,
            updatedAt = now,
        )
        return StudioState(
            prefs = Prefs(),
            projects = listOf(draft, ready),
            activeId = draft.id,
        )
    }
}
