package de.spardirekt.ugcclean.model

import kotlinx.serialization.Serializable

enum class SpeechLanguage {
    DE,
    RU,
    ;

    val jsonValue: String
        get() = if (this == DE) "DEUTSCH" else "RUSSKIJ"
}

enum class ProjectStatus {
    DRAFT,
    RUNNING,
    READY,
    ERROR,
}

enum class PipelineStage {
    PHOTOS,
    ANALYZE,
    PROMPT,
    COPY,
    DONE,
}

@Serializable
data class ProductPlan(
    val productName: String,
    val category: String,
    val visibleFeatures: List<String>,
    val movingParts: List<String>,
    val useCase: String,
    val desire: String,
    val setting: String,
    val camera: String,
    val action: String,
    val humanBehaviour: String,
    val lighting: String,
    val spokenHookDe: String,
    val spokenHookRu: String,
)

@Serializable
data class CopyPack(
    val caption: String,
    val hashtags: List<String>,
    val details: String,
)

@Serializable
data class ProjectRecord(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ProjectStatus,
    val language: SpeechLanguage,
    val photoUris: List<String>,
    val thumbnailUri: String? = null,
    val stage: PipelineStage = PipelineStage.PHOTOS,
    val progressPercent: Int = 0,
    val plan: ProductPlan? = null,
    val veoPrompt: String = "",
    val copyPack: CopyPack? = null,
    val errorMessage: String? = null,
) {
    fun videoPackage(): String {
        val tags = copyPack?.hashtags.orEmpty().joinToString(" ")
        return listOf(veoPrompt.trim(), copyPack?.caption.orEmpty().trim(), tags)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }
}

data class EncodedImage(
    val mime: String,
    val dataUrl: String,
)
