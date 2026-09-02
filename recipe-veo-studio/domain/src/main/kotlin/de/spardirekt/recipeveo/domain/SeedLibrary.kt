package de.spardirekt.recipeveo.domain

object SeedLibrary {
    fun populated(clock: AppClock): StudioState {
        val now = clock.nowMillis()
        val recipes = listOf(
            recipe(
                clock,
                now - 4,
                title = "Буррата с мёдом",
                subject = "burrata on a dark plate, honey drizzle, basil leaf",
                kind = RecipeKind.FOOD,
                style = VisualStyle.MACRO,
                setting = "Dark stone tabletop, one warm key from the left, black negative space.",
                lock = listOf("torn burrata, glossy stracciatella, amber honey thread, single basil leaf"),
                beats = listOf(
                    "honey thread catching light before it hits the cheese",
                    "full plate: torn burrata, basil, dark ceramic",
                    "slow drizzle, honey pools in the cream",
                    "hero still, steamless, cheese gloss locked",
                ),
                voice = VoiceLang.RU,
                wish = "No people. Only the plate.",
            ),
            recipe(
                clock,
                now - 3,
                title = "Velvet Gold Night Cream",
                subject = "ivory night-cream jar with gold lid",
                kind = RecipeKind.PRODUCT,
                style = VisualStyle.STUDIO,
                setting = "Uncluttered premium studio.",
                lock = listOf("gold cap", "ivory jar", "cream texture", "short jar silhouette", "visible brand mark"),
                beats = listOf(
                    "gold lid catch light",
                    "full ivory jar identity",
                    "one hand lifts the lid, cream visible at the rim",
                    "jar hero hold, label readable",
                ),
                voice = VoiceLang.DE,
            ),
            recipe(
                clock,
                now - 2,
                title = "Эспрессо в чашке",
                subject = "single espresso in a thick ceramic cup, crema intact",
                kind = RecipeKind.DRINK,
                style = VisualStyle.SATISFYING,
                setting = "Top-down oak table, cup centered, no extra props.",
                lock = listOf("thick beige crema", "small ceramic cup", "dark espresso body", "no latte art"),
                beats = listOf(
                    "crema surface micro-bubbles in macro",
                    "cup silhouette, saucer, wood grain",
                    "slow pour finishing, crema unbroken",
                    "hero top-down, spoon out of frame",
                ),
                voice = VoiceLang.OFF,
            ),
            recipe(
                clock,
                now - 1,
                title = "Лён на ветру",
                subject = "sand-colored linen dress moving in late daylight",
                kind = RecipeKind.LIFESTYLE,
                style = VisualStyle.CINEMATIC,
                setting = "Open field edge, golden hour, wind from camera left.",
                lock = listOf("sand linen", "visible weave", "no logos", "natural waist seam"),
                beats = listOf(
                    "fabric edge snapping in wind",
                    "full dress on a person, face not hero",
                    "one step, cloth follows late",
                    "hero still, sun rim on the shoulder seam",
                ),
                voice = VoiceLang.RU,
                wish = "No brand names. Keep it quiet.",
            ),
        )
        return StudioState(prefs = Prefs(theme = ThemeMode.DARK, defaultVoice = VoiceLang.RU), recipes = recipes)
    }

    private fun recipe(
        clock: AppClock,
        createdAt: Long,
        title: String,
        subject: String,
        kind: RecipeKind,
        style: VisualStyle,
        setting: String,
        lock: List<String>,
        beats: List<String>,
        voice: VoiceLang,
        wish: String = "",
    ): Recipe {
        val roles = BeatRole.entries
        val shotBeats = roles.mapIndexed { index, role ->
            ShotBeat(role, index * 2.0, index * 2.0 + 2.0, beats[index])
        }
        val draft = Recipe(
            id = newId(),
            title = title,
            subject = subject,
            kind = kind,
            style = style,
            setting = setting,
            lockNotes = lock,
            beats = shotBeats,
            voice = voice,
            wish = wish,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
        return draft.copy(compiled = RecipeCompiler.compile(draft, clock))
    }
}
