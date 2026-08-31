package de.spardirekt.tiktokshop.data.model

import kotlinx.serialization.Serializable

enum class ImageKind { Product, Description }

data class ImageSlot(
    val kind: ImageKind,
    val uri: String? = null,
    val fileName: String? = null,
)

data class EncodedImage(
    val base64: String,
    val mime: String,
)

@Serializable
data class ProductFacts(
    val name: String = "Nicht erkennbar",
    val dimensions: String = "Nicht erkennbar",
    val capacity: String = "Nicht erkennbar",
    val material: String = "Nicht erkennbar",
    val weight: String = "Nicht erkennbar",
    val color: String = "Nicht erkennbar",
    val includedItems: List<String> = emptyList(),
    val keyFeatures: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val useCases: List<String> = emptyList(),
)

@Serializable
data class GenerateResult(
    val productFacts: ProductFacts = ProductFacts(),
    val hooks: List<String> = emptyList(),
    val title: String = "",
    val hashtags: List<String> = emptyList(),
    val bannerText: List<String> = emptyList(),
    val bannerPrompt: String = "",
    val voiceoverText: String = "",
    val musicSuggestion: String = "",
    val soundEffects: String = "",
    val veoPrompt: String = "",
    val liveScript: String = "",
)

@Serializable
data class AnthropicResponse(
    val content: List<AnthropicContent> = emptyList(),
    val error: AnthropicError? = null,
)

@Serializable
data class AnthropicContent(
    val type: String? = null,
    val text: String? = null,
)

@Serializable
data class AnthropicError(
    val message: String? = null,
    val type: String? = null,
)

object VideoStyles {
    val all = listOf(
        "Trendy & viral",
        "Aesthetic & ruhig",
        "Energetisch & schnell",
        "Storytelling",
        "Unboxing",
        "Tutorial / How-to",
        "Before & After",
    )
}

object Tones {
    val all = listOf(
        "Freundlich & persönlich",
        "Aufgeregt & enthusiastisch",
        "Professionell & seriös",
        "Humorvoll & locker",
        "Inspirierend & motivierend",
    )
}

fun GenerateResult.masterCopy(): String = listOf(
    "=== VIDEO LENGTH ===\n8 Seconds",
    "=== BANNER TEXT ===\n${bannerText.joinToString("\n")}",
    "=== VOICE SCRIPT ===\n$voiceoverText",
    "=== MUSIC ===\n$musicSuggestion",
    "=== SOUND EFFECTS ===\n$soundEffects",
    "=== VEO 3.1 PROMPT ===\n$veoPrompt",
).joinToString("\n\n")

fun GenerateResult.veoKomplett(): String = listOf(
    "=== VIDEO LENGTH ===\n8 Seconds",
    "=== VEO 3.1 PROMPT ===\n$veoPrompt",
    "=== GERMAN VOICEOVER ===\n$voiceoverText",
    "=== MUSIC ===\n$musicSuggestion",
    "=== SOUND EFFECTS ===\n$soundEffects",
).joinToString("\n\n")

fun GenerateResult.copyAll(): String = listOf(
    "=== Produktdaten ===\n${productFacts.asPlainText()}",
    "=== TikTok Titel ===\n$title",
    "=== 5 Hook Ideas ===\n${hooks.mapIndexed { i, h -> "${i + 1}. $h" }.joinToString("\n")}",
    "=== Hashtags ===\n${hashtags.joinToString(" ")}",
    "=== Banner Text ===\n${bannerText.joinToString("\n")}",
    "=== Banner Prompt ===\n$bannerPrompt",
    "=== Voice Script ===\n$voiceoverText",
    "=== Music Suggestion ===\n$musicSuggestion",
    "=== Sound Effects ===\n$soundEffects",
    "=== Veo 3.1 Prompt ===\n$veoPrompt",
    "=== TikTok Live Script ===\n$liveScript",
).joinToString("\n\n")

fun ProductFacts.asPlainText(): String = buildString {
    appendLine("Produktname: $name")
    appendLine("Maße: $dimensions")
    appendLine("Kapazität: $capacity")
    appendLine("Material: $material")
    appendLine("Gewicht: $weight")
    appendLine("Farbe: $color")
    appendLine("Lieferumfang: ${includedItems.ifEmpty { listOf("Nicht erkennbar") }.joinToString(", ")}")
    appendLine("Features: ${keyFeatures.ifEmpty { listOf("Nicht erkennbar") }.joinToString(", ")}")
    appendLine("Warnhinweise: ${warnings.ifEmpty { listOf("Nicht erkennbar") }.joinToString(", ")}")
    append("Anwendung: ${useCases.ifEmpty { listOf("Nicht erkennbar") }.joinToString(", ")}")
}
