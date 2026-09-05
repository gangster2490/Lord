package de.spardirekt.ugcagent.v3.pipeline

enum class PipelineStage {
    IDLE,
    IMAGES_READY,
    CONSISTENCY_CHECK,
    PRODUCT_ANALYSIS,
    IDENTITY_FINGERPRINT,
    IDENTITY_EXTRACTION,
    IDENTITY_READINESS,
    EVIDENCE_VALIDATION,
    FIRST_FRAME,
    FIRST_FRAME_SELECTION,
    ACTION_RISK,
    SCENE_GENERATION,
    MOTION_RISK_SELECTION,
    FINAL_IDENTITY_LOCK,
    HOOK_GENERATION,
    PROMPT_GENERATION,
    VEO_PROMPT_GENERATION,
    PROMPT_QUALITY_CHECK,
    COMPLIANCE,
    CAPTION,
    CAPTION_GENERATION,
    HASHTAG_GENERATION,
    FINAL_QUALITY_CHECK,
    EXPORT_READY,
    READY,
    PAUSED,
    ERROR,
    ;

    companion object {
        fun fromName(value: String?): PipelineStage {
            val name = value?.trim().orEmpty()
            return when (name.uppercase()) {
                "IDENTITY_FINGERPRINT" -> IDENTITY_EXTRACTION
                "IDENTITY_READINESS", "CONSISTENCY_CHECK" -> EVIDENCE_VALIDATION
                "FIRST_FRAME" -> FIRST_FRAME_SELECTION
                "ACTION_RISK", "SCENE_GENERATION", "FINAL_IDENTITY_LOCK" -> MOTION_RISK_SELECTION
                "PROMPT_GENERATION" -> VEO_PROMPT_GENERATION
                "CAPTION" -> CAPTION_GENERATION
                "PROMPT_QUALITY_CHECK", "COMPLIANCE" -> FINAL_QUALITY_CHECK
                "EXPORT_READY" -> READY
                else -> entries.firstOrNull { it.name.equals(name, true) } ?: IDLE
            }
        }

        val runnableOrder: List<PipelineStage> = listOf(
            IMAGES_READY,
            PRODUCT_ANALYSIS,
            IDENTITY_EXTRACTION,
            EVIDENCE_VALIDATION,
            FIRST_FRAME_SELECTION,
            MOTION_RISK_SELECTION,
            HOOK_GENERATION,
            VEO_PROMPT_GENERATION,
            CAPTION_GENERATION,
            HASHTAG_GENERATION,
            FINAL_QUALITY_CHECK,
            READY,
        )
    }
}

class PipelinePaused(val reason: String, val stage: PipelineStage) : Exception(reason)
