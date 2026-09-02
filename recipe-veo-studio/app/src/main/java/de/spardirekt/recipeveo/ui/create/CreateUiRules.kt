package de.spardirekt.recipeveo.ui.create

import de.spardirekt.recipeveo.model.GenerationStage

/**
 * Stage bookkeeping for the progress rail and the sticky action bar. Kept out of
 * the composables so the two views can never disagree about which step is live.
 */
object GenerationProgress {

    val labels = listOf(
        "Анализ фотографий",
        "Понимание товара",
        "Создание рекламной идеи",
        "Создание VEO Prompt",
        "Проверка результата",
        "Финализация"
    )

    private val runningStages = listOf(
        GenerationStage.PHOTO_ANALYSIS,
        GenerationStage.PRODUCT_MODEL,
        GenerationStage.CREATIVE_DIRECTOR,
        GenerationStage.FINAL_PROMPT,
        GenerationStage.FINAL_VALIDATION,
        GenerationStage.FINALIZATION
    )

    val stepCount: Int = labels.size

    fun isRunning(stage: GenerationStage): Boolean = stage in runningStages

    /** Index of the step currently in flight, used to highlight the rail. */
    fun activeIndex(stage: GenerationStage): Int = when (stage) {
        GenerationStage.DONE -> labels.lastIndex
        else -> runningStages.indexOf(stage).coerceAtLeast(0)
    }

    /** Last index already finished. -1 while the first step is still running. */
    fun completedThrough(stage: GenerationStage): Int = when (stage) {
        GenerationStage.DONE -> labels.lastIndex
        else -> (runningStages.indexOf(stage) - 1).coerceAtLeast(-1)
    }

    /**
     * One line for the action bar so progress stays visible without scrolling
     * down to the rail, e.g. "Шаг 2 из 6 · Понимание товара".
     */
    fun statusLine(stage: GenerationStage): String {
        if (stage == GenerationStage.DONE) return "Готово"
        val index = runningStages.indexOf(stage)
        if (index < 0) return ""
        return "Шаг ${index + 1} из $stepCount · ${labels[index]}"
    }

    /** Whether the rail is worth showing at all. */
    fun showsRail(stage: GenerationStage, isGenerating: Boolean): Boolean =
        isGenerating || isRunning(stage)
}

/** Whether the form is complete enough to start a run, and why not if it isn't. */
object CreateFormRules {

    fun canGenerate(photoCount: Int, isGenerating: Boolean): Boolean =
        photoCount > 0 && !isGenerating

    /**
     * Shown under the disabled button. The old flow let the user tap a live
     * button with no photos and only then printed an error far down the page.
     */
    fun blockingHint(photoCount: Int, isGenerating: Boolean): String = when {
        isGenerating -> ""
        photoCount == 0 -> "Добавьте хотя бы одно фото товара"
        else -> ""
    }

    fun photoCountLabel(photoCount: Int): String =
        if (photoCount == 0) "Фото пока не загружены" else "$photoCount фото загружено"
}
