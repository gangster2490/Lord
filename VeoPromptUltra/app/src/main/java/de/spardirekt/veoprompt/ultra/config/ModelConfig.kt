package de.spardirekt.veoprompt.ultra.config

/**
 * Single source of truth for OpenAI model IDs shown in Settings.
 * UI labels never invent IDs — they map 1:1 onto this catalog.
 */
object ModelConfig {

    enum class Profile(
        val label: String,
        val modelId: String,
        val hint: String
    ) {
        BALANCED(
            label = "Balanced",
            modelId = "gpt-4o",
            hint = "Качество и скорость"
        ),
        BEST_QUALITY(
            label = "Best Quality",
            modelId = "gpt-4.1",
            hint = "Максимальная точность анализа"
        ),
        ECONOMY(
            label = "Economy",
            modelId = "gpt-4o-mini",
            hint = "Быстрее и дешевле"
        );

        companion object {
            val DEFAULT: Profile = BALANCED

            fun fromModelId(id: String?): Profile {
                val value = id?.trim().orEmpty()
                return entries.firstOrNull { it.modelId == value } ?: DEFAULT
            }
        }
    }

    val ids: Set<String> = Profile.entries.map { it.modelId }.toSet()

    fun sanitize(stored: String?): String = Profile.fromModelId(stored).modelId

    fun profile(stored: String?): Profile = Profile.fromModelId(stored)

    fun imageDetail(model: String): String = "high"

    fun isGpt5Family(model: String): Boolean = model.startsWith("gpt-5")

    fun completionBudget(visibleOutput: Int): Int = visibleOutput.coerceAtMost(16_384)

    fun connectTimeoutSeconds(): Long = 30L

    fun photoAnalysisTimeoutSeconds(): Long = 180L

    fun productModelTimeoutSeconds(): Long = 120L

    fun creativeDirectorTimeoutSeconds(): Long = 120L

    fun finalPromptTimeoutSeconds(): Long = 240L

    fun targetedRepairTimeoutSeconds(): Long = 120L
}
