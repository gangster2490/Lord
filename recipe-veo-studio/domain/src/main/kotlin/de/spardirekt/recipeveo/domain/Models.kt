package de.spardirekt.recipeveo.domain

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class RecipeKind { FOOD, DRINK, PRODUCT, BEAUTY, LIFESTYLE }

@Serializable
enum class VisualStyle { STUDIO, MACRO, SATISFYING, LIFESTYLE, CINEMATIC, STREET }

@Serializable
enum class VoiceLang { DE, RU, OFF }

@Serializable
enum class BeatRole { HOOK, IDENTITY, FEATURE, HERO }

@Serializable
data class Prefs(
    val defaultVoice: VoiceLang = VoiceLang.RU,
    val theme: ThemeMode = ThemeMode.DARK,
)

@Serializable
data class ShotBeat(
    val role: BeatRole,
    val startSec: Double,
    val endSec: Double,
    val action: String,
)

@Serializable
data class CompiledPrompt(
    val veoPrompt: String,
    val voiceover: String,
    val title: String,
    val hashtags: List<String>,
    val compiledAt: Long,
)

@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val subject: String,
    val kind: RecipeKind,
    val style: VisualStyle,
    val setting: String,
    val lockNotes: List<String>,
    val beats: List<ShotBeat>,
    val voice: VoiceLang,
    val wish: String = "",
    val onScreenText: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val compiled: CompiledPrompt? = null,
)

fun newId(): String = UUID.randomUUID().toString()

fun defaultBeats(): List<ShotBeat> = listOf(
    ShotBeat(BeatRole.HOOK, 0.0, 2.0, ""),
    ShotBeat(BeatRole.IDENTITY, 2.0, 4.0, ""),
    ShotBeat(BeatRole.FEATURE, 4.0, 6.0, ""),
    ShotBeat(BeatRole.HERO, 6.0, 8.0, ""),
)

fun RecipeKind.label(): String = when (this) {
    RecipeKind.FOOD -> "Еда"
    RecipeKind.DRINK -> "Напиток"
    RecipeKind.PRODUCT -> "Продукт"
    RecipeKind.BEAUTY -> "Бьюти"
    RecipeKind.LIFESTYLE -> "Лайфстайл"
}

fun VisualStyle.label(): String = when (this) {
    VisualStyle.STUDIO -> "Студия"
    VisualStyle.MACRO -> "Макро"
    VisualStyle.SATISFYING -> "Satisfying"
    VisualStyle.LIFESTYLE -> "Lifestyle"
    VisualStyle.CINEMATIC -> "Кино"
    VisualStyle.STREET -> "Улица"
}

fun VoiceLang.label(): String = when (this) {
    VoiceLang.RU -> "Русский"
    VoiceLang.DE -> "Deutsch"
    VoiceLang.OFF -> "Без голоса"
}

fun BeatRole.label(): String = when (this) {
    BeatRole.HOOK -> "Хук"
    BeatRole.IDENTITY -> "Объект"
    BeatRole.FEATURE -> "Демо"
    BeatRole.HERO -> "Герой"
}
