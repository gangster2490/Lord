package de.spardirekt.veoprompt.ultra.ui.create

/**
 * One-shot Result navigation for a single generate run.
 */
class ResultNavigationGate {
    private var offered = false

    @Synchronized
    fun onGenerationStarted() {
        offered = false
    }

    @Synchronized
    fun reset() {
        offered = false
    }

    @Synchronized
    fun offer(projectId: String): String? {
        if (projectId.isBlank() || offered) return null
        offered = true
        return projectId
    }
}

object CreateFormRules {
    fun canGenerate(photoCount: Int, isGenerating: Boolean): Boolean =
        photoCount in 1..15 && !isGenerating

    fun blockingHint(photoCount: Int, isGenerating: Boolean): String = when {
        isGenerating -> ""
        photoCount == 0 -> "Добавьте хотя бы одно фото товара"
        photoCount > 15 -> "Максимум 15 фото"
        else -> ""
    }
}

object GenerationProgress {
    val labels = listOf(
        "Анализ фотографий",
        "Понимание товара",
        "Фиксация внешнего вида",
        "Создание рекламной идеи",
        "Создание VEO Prompt",
        "Проверка точности",
        "Финализация"
    )

    fun activeIndex(stageName: String): Int = when (stageName) {
        "PHOTO_ANALYSIS" -> 0
        "PRODUCT_MODEL" -> 1
        "VISUAL_LOCK" -> 2
        "CREATIVE_DIRECTOR" -> 3
        "FINAL_PROMPT" -> 4
        "FINAL_VALIDATION", "TARGETED_REPAIR" -> 5
        "FINALIZATION" -> 6
        "DONE" -> 6
        else -> 0
    }
}
