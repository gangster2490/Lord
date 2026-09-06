package de.spardirekt.ugcagent.v3.pipeline

import org.json.JSONArray
import org.json.JSONObject

/**
 * User-facing pipeline progress. Collapses internal aliases into one ordered list.
 */
object PipelineProgress {
    val steps: List<PipelineStage> = PipelineStage.runnableOrder.filter { it != PipelineStage.READY }

    fun displayStage(stage: PipelineStage): PipelineStage = PipelineStage.fromName(stage.name).let { mapped ->
        if (mapped == PipelineStage.READY || mapped == PipelineStage.EXPORT_READY) PipelineStage.READY
        else if (mapped == PipelineStage.PAUSED || mapped == PipelineStage.ERROR || mapped == PipelineStage.IDLE) mapped
        else if (mapped in steps) mapped
        else steps.first()
    }

    fun index(stage: PipelineStage): Int {
        val display = displayStage(stage)
        if (display == PipelineStage.READY) return steps.size
        val idx = steps.indexOf(display)
        return if (idx >= 0) idx + 1 else 1
    }

    fun percent(completed: Collection<PipelineStage>, stage: PipelineStage): Int {
        val display = displayStage(stage)
        if (display == PipelineStage.READY) return 100
        if (display == PipelineStage.ERROR || display == PipelineStage.PAUSED) {
            val done = steps.count { completed.contains(it) }
            return ((done * 100) / steps.size).coerceIn(0, 99)
        }
        val done = steps.count { completed.contains(it) }
        return ((done * 100) / steps.size).coerceIn(0, 99)
    }

    fun label(stage: PipelineStage, russian: Boolean): String {
        val display = displayStage(stage)
        return if (russian) labelRu(display) else labelDe(display)
    }

    fun toJson(
        stage: PipelineStage,
        completed: Collection<PipelineStage>,
        running: Boolean,
        russian: Boolean,
    ): JSONObject {
        val display = displayStage(stage)
        val stepsJson = JSONArray()
        steps.forEach { step ->
            val done = completed.contains(step) || display == PipelineStage.READY
            val current = !done && step == display
            stepsJson.put(
                JSONObject()
                    .put("id", step.name)
                    .put("label", label(step, russian))
                    .put("done", done)
                    .put("current", current),
            )
        }
        return JSONObject()
            .put("stage", stage.name)
            .put("label", label(display, russian))
            .put("index", index(stage))
            .put("total", steps.size)
            .put("percent", percent(completed, stage))
            .put("running", running)
            .put("steps", stepsJson)
    }

    private fun labelDe(stage: PipelineStage): String = when (stage) {
        PipelineStage.IMAGES_READY -> "Fotos geprüft"
        PipelineStage.PRODUCT_ANALYSIS -> "Produkt verstehen"
        PipelineStage.IDENTITY_EXTRACTION -> "Identität sichern"
        PipelineStage.EVIDENCE_VALIDATION -> "Fotos abgleichen"
        PipelineStage.FIRST_FRAME_SELECTION -> "First Frame wählen"
        PipelineStage.PURCHASE_APPEAL -> "Verkaufsidee"
        PipelineStage.MOTION_RISK_SELECTION -> "Sichere Aktion"
        PipelineStage.HOOK_GENERATION -> "Gesprochene Zeile"
        PipelineStage.VEO_PROMPT_GENERATION -> "Video-Prompt"
        PipelineStage.CAPTION_GENERATION -> "Caption"
        PipelineStage.HASHTAG_GENERATION -> "Hashtags"
        PipelineStage.FINAL_QUALITY_CHECK -> "Qualität prüfen"
        PipelineStage.READY, PipelineStage.EXPORT_READY -> "Fertig"
        PipelineStage.PAUSED -> "Pause"
        PipelineStage.ERROR -> "Fehler"
        else -> "Pipeline läuft"
    }

    private fun labelRu(stage: PipelineStage): String = when (stage) {
        PipelineStage.IMAGES_READY -> "Проверка фото"
        PipelineStage.PRODUCT_ANALYSIS -> "Понимание товара"
        PipelineStage.IDENTITY_EXTRACTION -> "Фиксация идентичности"
        PipelineStage.EVIDENCE_VALIDATION -> "Сверка фото"
        PipelineStage.FIRST_FRAME_SELECTION -> "Выбор First Frame"
        PipelineStage.PURCHASE_APPEAL -> "Идея продажи"
        PipelineStage.MOTION_RISK_SELECTION -> "Безопасное действие"
        PipelineStage.HOOK_GENERATION -> "Реплика"
        PipelineStage.VEO_PROMPT_GENERATION -> "Видео-промпт"
        PipelineStage.CAPTION_GENERATION -> "Caption"
        PipelineStage.HASHTAG_GENERATION -> "Хэштеги"
        PipelineStage.FINAL_QUALITY_CHECK -> "Проверка качества"
        PipelineStage.READY, PipelineStage.EXPORT_READY -> "Готово"
        PipelineStage.PAUSED -> "Пауза"
        PipelineStage.ERROR -> "Ошибка"
        else -> "Пайплайн работает"
    }
}
