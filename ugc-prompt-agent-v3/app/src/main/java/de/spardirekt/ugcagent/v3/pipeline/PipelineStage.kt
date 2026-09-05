package de.spardirekt.ugcagent.v3.pipeline

enum class PipelineStage {
    IDLE,
    IMAGES_READY,
    CONSISTENCY_CHECK,
    PRODUCT_ANALYSIS,
    IDENTITY_FINGERPRINT,
    IDENTITY_READINESS,
    FIRST_FRAME,
    ACTION_RISK,
    SCENE_GENERATION,
    FINAL_IDENTITY_LOCK,
    PROMPT_GENERATION,
    PROMPT_QUALITY_CHECK,
    COMPLIANCE,
    CAPTION,
    EXPORT_READY,
    PAUSED,
    ERROR,
    ;

    companion object {
        fun fromName(value: String?): PipelineStage =
            entries.firstOrNull { it.name.equals(value?.trim().orEmpty(), true) } ?: IDLE

        val runnableOrder: List<PipelineStage> = listOf(
            IMAGES_READY,
            CONSISTENCY_CHECK,
            PRODUCT_ANALYSIS,
            IDENTITY_FINGERPRINT,
            IDENTITY_READINESS,
            FIRST_FRAME,
            ACTION_RISK,
            SCENE_GENERATION,
            FINAL_IDENTITY_LOCK,
            PROMPT_GENERATION,
            PROMPT_QUALITY_CHECK,
            COMPLIANCE,
            CAPTION,
            EXPORT_READY,
        )
    }
}

class PipelinePaused(val reason: String, val stage: PipelineStage) : Exception(reason)
