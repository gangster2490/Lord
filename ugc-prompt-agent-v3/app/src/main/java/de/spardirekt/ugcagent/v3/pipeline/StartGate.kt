package de.spardirekt.ugcagent.v3.pipeline

import org.json.JSONObject

object StartGate {
    fun evaluate(
        imageCount: Int,
        language: String,
        stage: String,
        pausedReason: String?,
        busy: Boolean,
        minImages: Int,
        maxImages: Int,
    ): JSONObject {
        val imagesOk = imageCount in minImages..maxImages
        val languageOk = language.equals("DEUTSCH", true) || language.equals("РУССКИЙ", true)
        val hardConflict = pausedReason == PauseReasons.DIFFERENT_PRODUCTS &&
            stage.equals(PipelineStage.PAUSED.name, true)
        val stalePauseIgnored = !hardConflict && (
            stage.equals(PipelineStage.PAUSED.name, true) ||
                pausedReason == PauseReasons.LOW_CONSISTENCY ||
                pausedReason == PauseReasons.READINESS_HIGH
            )
        val enabled = imagesOk && languageOk && !hardConflict && !busy
        return JSONObject()
            .put("imageCount", imageCount)
            .put("imagesOk", imagesOk)
            .put("minImages", minImages)
            .put("maxImages", maxImages)
            .put("language", language)
            .put("languageOk", languageOk)
            .put("stage", stage)
            .put("pausedReason", pausedReason ?: "")
            .put("hardConflict", hardConflict)
            .put("stalePauseIgnored", stalePauseIgnored)
            .put("busy", busy)
            .put("startEnabled", enabled)
    }
}
