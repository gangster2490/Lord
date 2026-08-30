package de.spardirekt.tiktokshop.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ImageKind { PRODUCT, DESCRIPTION }

data class EncodedImage(
    val base64: String,
    val mime: String,
    val displayName: String,
    val uriString: String,
)

data class ImageSlot(
    val index: Int,
    val kind: ImageKind,
    val title: String,
    val subtitle: String,
    val required: Boolean,
    val image: EncodedImage? = null,
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
data class GeneratedContent(
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
data class ProductDna(
    val name: String = "",
    val category: String = "",
    val shape: String = "",
    val material: String = "",
    val color: String = "",
    val details: String = "",
    @SerialName("do_not_change") val doNotChange: String = "",
    @SerialName("anti_distortion") val antiDistortion: String = "",
)

@Serializable
data class VeoHistoryEntry(
    val id: Long,
    val date: String,
    val productName: String,
    val prompt: String,
    val resultPath: String = "",
    val resultUrl: String = "",
    val dna: ProductDna? = null,
)

object CreatorOptions {
    val videoStyles = listOf(
        "Trendy & viral",
        "Aesthetic & ruhig",
        "Energetisch & schnell",
        "Storytelling",
        "Unboxing",
        "Tutorial / How-to",
        "Before & After",
    )

    val tones = listOf(
        "Freundlich & persönlich",
        "Aufgeregt & enthusiastisch",
        "Professionell & seriös",
        "Humorvoll & locker",
        "Inspirierend & motivierend",
    )
}

fun defaultImageSlots(): List<ImageSlot> = listOf(
    ImageSlot(
        index = 0,
        kind = ImageKind.PRODUCT,
        title = "Bild 1 – Produktbild",
        subtitle = "Pflichtfeld · JPG PNG WEBP · max 10 MB",
        required = true,
    ),
    ImageSlot(
        index = 1,
        kind = ImageKind.PRODUCT,
        title = "Bild 2 – Produktbild",
        subtitle = "Optional · weitere Perspektive, Detail, Anwendung",
        required = false,
    ),
    ImageSlot(
        index = 2,
        kind = ImageKind.PRODUCT,
        title = "Bild 3 – Produktbild",
        subtitle = "Optional · Verpackung, Lieferumfang, Detail",
        required = false,
    ),
    ImageSlot(
        index = 3,
        kind = ImageKind.DESCRIPTION,
        title = "Bild 4 – Beschreibung / Spezifikationen",
        subtitle = "Optional · Screenshot mit Features, Maßen, Material (OCR)",
        required = false,
    ),
)
