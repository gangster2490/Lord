package de.spardirekt.recipeveo.domain

data class Recipe(
    val dish: String,
    val servings: String,
    val time: String,
    val ingredients: List<String>,
    val steps: List<String>,
) {
    fun asText(): String = buildString {
        appendLine(dish.replaceFirstChar { it.uppercase() })
        appendLine("Порции: $servings")
        appendLine("Время: $time")
        appendLine()
        appendLine("Ингредиенты")
        ingredients.forEach { appendLine("• $it") }
        appendLine()
        appendLine("Приготовление")
        steps.forEachIndexed { i, step -> appendLine("${i + 1}. $step") }
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
) {
    init {
        require(hashtags.size == 5) { "Нужно ровно 5 хештегов." }
    }

    fun copyPrompt(): String = veoPrompt
}

object StudioRules {
    const val MIN_DISH = 2
    const val MAX_DISH = 48
    fun canCreate(name: String) = name.trim().length in MIN_DISH..MAX_DISH
}
