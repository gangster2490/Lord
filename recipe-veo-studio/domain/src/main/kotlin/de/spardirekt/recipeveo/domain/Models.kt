package de.spardirekt.recipeveo.domain

data class Recipe(
    val dish: String,
    val servings: String,
    val time: String,
    val ingredients: List<String>,
    val steps: List<String>,
) {
    fun asText(): String = buildString {
        if (servings.isNotBlank()) appendLine("Порции: $servings")
        if (time.isNotBlank()) appendLine("Время: $time")
        if (servings.isNotBlank() || time.isNotBlank()) appendLine()
        if (ingredients.isNotEmpty()) {
            appendLine("Ингредиенты")
            ingredients.forEach { appendLine("• $it") }
            appendLine()
        }
        if (steps.isNotEmpty()) {
            appendLine("Приготовление")
            steps.forEachIndexed { i, step -> appendLine("${i + 1}. $step") }
        }
    }.trim()
}

data class CulinaryPackage(
    val dish: String,
    val recipe: Recipe,
    val veoPrompt: String,
    val negativePrompt: String,
    val voiceover: String,
    val tiktokTitle: String,
    val hashtags: List<String>,
    val createdAt: Long,
    val fromOpenAi: Boolean = false,
) {
    fun geminiPrompt(): String = veoPrompt.trim()

    fun fullPackage(): String = buildString {
        appendLine("БЛЮДО")
        appendLine(dish)
        appendLine()
        appendLine("РЕЦЕПТ")
        appendLine(recipe.asText())
        appendLine()
        appendLine("ПРОМПТ VEO 3.1 · 8 СЕКУНД")
        appendLine(veoPrompt.trim())
        appendLine()
        appendLine("НЕГАТИВНЫЙ ПРОМПТ")
        appendLine(negativePrompt.trim())
        appendLine()
        appendLine("ОЗВУЧКА")
        appendLine(voiceover.trim())
        appendLine()
        appendLine("НАЗВАНИЕ TIKTOK")
        appendLine(tiktokTitle.trim())
        appendLine()
        appendLine("ХЕШТЕГИ")
        appendLine(hashtags.joinToString(" "))
    }.trim()
}

object StudioRules {
    const val MIN_DISH = 2
    const val MAX_DISH = 48
    fun canCreate(name: String) = name.trim().length in MIN_DISH..MAX_DISH
}
