package de.spardirekt.recipeveo.domain

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class VoiceLang { DE, RU, OFF }

@Serializable
enum class CreativeMode { Auto, Showcase, Demo, Lifestyle, Macro, Satisfying, Unboxing }

@Serializable
enum class ProjectStatus { Draft, Generating, Ready, Error }

@Serializable
enum class GenerationStage {
    IDLE, PHOTO_ANALYSIS, PRODUCT_MODEL, CREATIVE_DIRECTOR, FINAL_PROMPT, DONE, FAILED
}

@Serializable
data class Prefs(
    val theme: ThemeMode = ThemeMode.DARK,
    val defaultVoice: VoiceLang = VoiceLang.DE,
    val tiktokShop: Boolean = true,
)

@Serializable
data class PhotoRef(
    val id: String,
    val uri: String,
)

@Serializable
data class PromptPackage(
    val veoPrompt: String,
    val voiceover: String,
    val title: String,
    val hashtags: List<String>,
    val compiledAt: Long,
)

@Serializable
data class Project(
    val id: String,
    val title: String = "",
    val photos: List<PhotoRef> = emptyList(),
    val wish: String = "",
    val voice: VoiceLang = VoiceLang.DE,
    val creative: CreativeMode = CreativeMode.Auto,
    val tiktokShop: Boolean = true,
    val status: ProjectStatus = ProjectStatus.Draft,
    val stage: GenerationStage = GenerationStage.IDLE,
    val error: String = "",
    val result: PromptPackage? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

fun newId(): String = UUID.randomUUID().toString()

fun VoiceLang.label(): String = when (this) {
    VoiceLang.DE -> "Deutsch"
    VoiceLang.RU -> "Русский"
    VoiceLang.OFF -> "Без голоса"
}

fun CreativeMode.label(): String = when (this) {
    CreativeMode.Auto -> "Auto"
    CreativeMode.Showcase -> "Showcase"
    CreativeMode.Demo -> "Demo"
    CreativeMode.Lifestyle -> "Lifestyle"
    CreativeMode.Macro -> "Macro"
    CreativeMode.Satisfying -> "Satisfying"
    CreativeMode.Unboxing -> "Unboxing"
}

fun ProjectStatus.label(): String = when (this) {
    ProjectStatus.Draft -> "Черновик"
    ProjectStatus.Generating -> "Сборка"
    ProjectStatus.Ready -> "Готово"
    ProjectStatus.Error -> "Ошибка"
}

fun GenerationStage.label(): String = when (this) {
    GenerationStage.IDLE -> ""
    GenerationStage.PHOTO_ANALYSIS -> "Разбор фото"
    GenerationStage.PRODUCT_MODEL -> "Модель продукта"
    GenerationStage.CREATIVE_DIRECTOR -> "Режиссура"
    GenerationStage.FINAL_PROMPT -> "Финальный промпт"
    GenerationStage.DONE -> "Готово"
    GenerationStage.FAILED -> "Ошибка"
}

object StudioRules {
    const val MAX_PHOTOS = 15

    fun canGenerate(project: Project): Boolean = project.photos.isNotEmpty() && project.status != ProjectStatus.Generating

    val sectionOrder = listOf(
        "FORMAT", "REFERENCES", "PRODUCT LOCK", "SETTING", "SHOT SEQUENCE",
        "ON-SCREEN TEXT", "VOICEOVER", "AUDIO", "CRITICAL", "NEGATIVE PROMPT",
        "TITLE", "HASHTAGS",
    )

    fun looksComplete(prompt: String, hashtags: List<String>): Boolean =
        sectionOrder.all { heading ->
            Regex("^${Regex.escape(heading)}$", RegexOption.MULTILINE).containsMatchIn(prompt)
        } && prompt.contains("Exactly 8.0s") && hashtags.size == 5
}
