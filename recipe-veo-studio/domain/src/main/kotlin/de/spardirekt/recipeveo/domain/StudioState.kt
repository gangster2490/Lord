package de.spardirekt.recipeveo.domain

import kotlinx.serialization.Serializable

@Serializable
data class StudioState(
    val version: Int = 1,
    val prefs: Prefs = Prefs(),
    val recipes: List<Recipe> = emptyList(),
) {
    fun updatePrefs(theme: ThemeMode = prefs.theme, defaultVoice: VoiceLang = prefs.defaultVoice): StudioState =
        copy(prefs = prefs.copy(theme = theme, defaultVoice = defaultVoice))

    fun upsert(recipe: Recipe): StudioState {
        val without = recipes.filterNot { it.id == recipe.id }
        return copy(recipes = listOf(recipe) + without)
    }

    fun delete(id: String): StudioState = copy(recipes = recipes.filterNot { it.id == id })

    fun compile(id: String, clock: AppClock): StudioState {
        val recipe = recipes.firstOrNull { it.id == id } ?: return this
        val compiled = RecipeCompiler.compile(recipe, clock)
        return upsert(recipe.copy(compiled = compiled, updatedAt = clock.nowMillis()))
    }

    fun createBlank(clock: AppClock): Pair<StudioState, Recipe> {
        val now = clock.nowMillis()
        val recipe = Recipe(
            id = newId(),
            title = "",
            subject = "",
            kind = RecipeKind.FOOD,
            style = VisualStyle.STUDIO,
            setting = "",
            lockNotes = emptyList(),
            beats = defaultBeats(),
            voice = prefs.defaultVoice,
            createdAt = now,
            updatedAt = now,
        )
        return upsert(recipe) to recipe
    }

    fun recipe(id: String): Recipe? = recipes.firstOrNull { it.id == id }

    fun visible(query: String, kind: RecipeKind?): List<Recipe> {
        val needle = query.trim().lowercase()
        return recipes.filter { recipe ->
            (kind == null || recipe.kind == kind) &&
                (needle.isEmpty() ||
                    recipe.title.lowercase().contains(needle) ||
                    recipe.subject.lowercase().contains(needle) ||
                    recipe.setting.lowercase().contains(needle))
        }
    }

    companion object {
        val Empty = StudioState()
    }
}
