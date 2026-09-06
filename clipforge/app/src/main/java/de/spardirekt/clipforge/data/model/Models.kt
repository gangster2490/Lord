package de.spardirekt.clipforge.data.model

import kotlinx.serialization.Serializable

enum class Platform(
    val id: String,
    val labelRu: String,
    val labelEn: String,
    val captionMax: Int,
    val overlayMax: Int,
    val hashtagCount: Int,
) {
    TIKTOK_SHOP(
        id = "tiktok_shop",
        labelRu = "TikTok Shop",
        labelEn = "TikTok Shop",
        captionMax = 150,
        overlayMax = 28,
        hashtagCount = 8,
    ),
    REELS(
        id = "reels",
        labelRu = "Instagram Reels",
        labelEn = "Instagram Reels",
        captionMax = 180,
        overlayMax = 28,
        hashtagCount = 8,
    ),
    SHORTS(
        id = "shorts",
        labelRu = "YouTube Shorts",
        labelEn = "YouTube Shorts",
        captionMax = 100,
        overlayMax = 32,
        hashtagCount = 5,
    );

    companion object {
        fun fromId(id: String): Platform =
            entries.firstOrNull { it.id == id } ?: TIKTOK_SHOP
    }
}

enum class AdLength(val seconds: Int, val label: String) {
    EIGHT(8, "8 сек"),
    FIFTEEN(15, "15 сек");

    companion object {
        fun fromSeconds(value: Int): AdLength =
            if (value >= 15) FIFTEEN else EIGHT
    }
}

enum class AdFormula(val id: String, val labelRu: String) {
    HOOK_DEMO_CTA("hook_demo_cta", "Хук → демо → CTA"),
    UGC("ugc", "UGC / отзыв"),
    PROBLEM_SOLUTION("problem_solution", "Проблема → решение"),
    UNBOXING("unboxing", "Распаковка"),
    BEFORE_AFTER("before_after", "До / после"),
    ASMR("asmr", "ASMR / детали");

    companion object {
        fun fromId(id: String): AdFormula =
            entries.firstOrNull { it.id == id } ?: HOOK_DEMO_CTA
    }
}

enum class VisualStyle(val id: String, val labelRu: String) {
    CINEMATIC("cinematic", "Кино / премиум"),
    UGC_RAW("ugc_raw", "Живой UGC"),
    PREMIUM_DARK("premium_dark", "Тёмный люкс"),
    BRIGHT_POP("bright_pop", "Яркий поп"),
    LIFESTYLE("lifestyle", "Лайфстайл"),
    MINIMAL("minimal", "Минимализм");

    companion object {
        fun fromId(id: String): VisualStyle =
            entries.firstOrNull { it.id == id } ?: CINEMATIC
    }
}

enum class AdLanguage(val id: String, val label: String, val nativeName: String) {
    RU("ru", "RU", "русский"),
    DE("de", "DE", "Deutsch"),
    EN("en", "EN", "English");

    companion object {
        fun fromId(id: String): AdLanguage =
            entries.firstOrNull { it.id == id } ?: RU
    }
}

data class ProductPhoto(
    val uri: String,
    val fileName: String? = null,
)

data class EncodedImage(
    val base64: String,
    val mime: String,
) {
    fun dataUrl(): String = "data:$mime;base64,$base64"
}

@Serializable
data class ProductInsight(
    val name: String = "Не распознано",
    val category: String = "Не распознано",
    val visualLock: String = "Не распознано",
    val sellingAngle: String = "",
    val audience: String = "",
    val keyFeatures: List<String> = emptyList(),
)

@Serializable
data class StoryboardShot(
    val startSec: Double = 0.0,
    val endSec: Double = 0.0,
    val shot: String = "",
    val action: String = "",
    val overlay: String = "",
)

@Serializable
data class AdPackage(
    val product: ProductInsight = ProductInsight(),
    val hooks: List<String> = emptyList(),
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val onScreenTexts: List<String> = emptyList(),
    val cta: String = "",
    val storyboard: List<StoryboardShot> = emptyList(),
    val voiceover: String = "",
    val music: String = "",
    val soundEffects: String = "",
    val veoPrompt: String = "",
    val thumbnailPrompt: String = "",
    val whyItConverts: String = "",
)

@Serializable
data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    val platformId: String,
    val lengthSeconds: Int,
    val formulaId: String,
    val languageId: String,
    val productName: String,
    val thumbnailUri: String? = null,
    val ad: AdPackage,
)

data class GenerateBrief(
    val platform: Platform,
    val length: AdLength,
    val formula: AdFormula,
    val style: VisualStyle,
    val language: AdLanguage,
    val wish: String,
    val photoCount: Int,
)

fun Platform.canonicalCta(language: AdLanguage): String = when (this) {
    Platform.TIKTOK_SHOP -> when (language) {
        AdLanguage.RU -> "Сейчас в корзине"
        AdLanguage.DE -> "Jetzt unten im Warenkorb"
        AdLanguage.EN -> "Tap the cart below"
    }
    Platform.REELS -> when (language) {
        AdLanguage.RU -> "Товар в профиле"
        AdLanguage.DE -> "Produkt im Profil"
        AdLanguage.EN -> "Shop from profile"
    }
    Platform.SHORTS -> when (language) {
        AdLanguage.RU -> "Смотрите описание"
        AdLanguage.DE -> "Details in der Beschreibung"
        AdLanguage.EN -> "See description to shop"
    }
}

fun Platform.ctaForbidden(): List<String> = when (this) {
    Platform.TIKTOK_SHOP -> listOf("link in bio", "ссылка в био", "Link in Bio")
    Platform.REELS -> listOf("link in bio as only CTA")
    Platform.SHORTS -> listOf("link in bio")
}

fun AdPackage.copyCaptionPack(platform: Platform): String = buildString {
    appendLine(caption.trim())
    appendLine()
    append(hashtags.joinToString(" "))
    appendLine()
    appendLine()
    appendLine("CTA: ${cta.ifBlank { platform.canonicalCta(AdLanguage.RU) }}")
}

fun AdPackage.copyVeoPack(length: AdLength): String = listOf(
    "=== VIDEO LENGTH ===",
    "${length.seconds} seconds · 9:16",
    "",
    "=== VEO 3.1 PROMPT ===",
    veoPrompt.trim(),
    "",
    "=== VOICEOVER ===",
    voiceover.trim(),
    "",
    "=== MUSIC ===",
    music.trim(),
    "",
    "=== SOUND EFFECTS ===",
    soundEffects.trim(),
    "",
    "=== ON-SCREEN TEXT ===",
    onScreenTexts.joinToString("\n"),
).joinToString("\n")

fun AdPackage.copyAll(
    platform: Platform,
    length: AdLength,
    formula: AdFormula,
    language: AdLanguage,
): String = buildString {
    appendLine("CLIPFORGE · ${platform.labelEn} · ${length.seconds}s · ${formula.labelRu} · ${language.label}")
    appendLine()
    appendLine("=== PRODUCT ===")
    appendLine("Name: ${product.name}")
    appendLine("Category: ${product.category}")
    appendLine("Visual lock: ${product.visualLock}")
    appendLine("Angle: ${product.sellingAngle}")
    appendLine("Audience: ${product.audience}")
    appendLine("Features: ${product.keyFeatures.joinToString(", ")}")
    appendLine()
    appendLine("=== WHY IT CONVERTS ===")
    appendLine(whyItConverts)
    appendLine()
    appendLine("=== 5 HOOKS ===")
    hooks.forEachIndexed { i, hook -> appendLine("${i + 1}. $hook") }
    appendLine()
    appendLine("=== CAPTION ===")
    appendLine(caption)
    appendLine()
    appendLine("=== HASHTAGS ===")
    appendLine(hashtags.joinToString(" "))
    appendLine()
    appendLine("=== CTA ===")
    appendLine(cta)
    appendLine()
    appendLine("=== STORYBOARD ===")
    storyboard.forEach { shot ->
        appendLine("${shot.startSec}–${shot.endSec}s · ${shot.shot}")
        appendLine(shot.action)
        if (shot.overlay.isNotBlank()) appendLine("Overlay: ${shot.overlay}")
    }
    appendLine()
    appendLine("=== VOICEOVER ===")
    appendLine(voiceover)
    appendLine()
    appendLine("=== MUSIC ===")
    appendLine(music)
    appendLine()
    appendLine("=== SOUND EFFECTS ===")
    appendLine(soundEffects)
    appendLine()
    appendLine("=== VEO 3.1 PROMPT ===")
    appendLine(veoPrompt)
    appendLine()
    appendLine("=== THUMBNAIL PROMPT ===")
    appendLine(thumbnailPrompt)
}

fun AdPackage.guarded(brief: GenerateBrief): AdPackage {
    val cta = this.cta.ifBlank { brief.platform.canonicalCta(brief.language) }
    val hooks = hooks.map { it.trim() }.filter { it.isNotEmpty() }.take(5)
    val hashtags = hashtags.map { normalizeHashtag(it) }.filter { it.isNotEmpty() }
        .distinct()
        .take(brief.platform.hashtagCount)
    val caption = caption.trim().take(brief.platform.captionMax)
    val overlays = onScreenTexts.map { it.trim().take(brief.platform.overlayMax) }
        .filter { it.isNotEmpty() }
    val veo = ensureVeoDuration(veoPrompt, brief.length, brief.platform)
    return copy(
        hooks = hooks,
        caption = caption,
        hashtags = hashtags,
        onScreenTexts = overlays,
        cta = cta,
        veoPrompt = veo,
    )
}

fun normalizeHashtag(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val body = trimmed.removePrefix("#").replace(" ", "")
    if (body.isEmpty()) return ""
    return "#$body"
}

fun ensureVeoDuration(prompt: String, length: AdLength, platform: Platform): String {
    val header = "VIDEO LENGTH: Exactly ${length.seconds} seconds. 9:16 vertical. ${platform.labelEn}."
    val trimmed = prompt.trim()
    return if (trimmed.contains("VIDEO LENGTH:", ignoreCase = true)) {
        trimmed
    } else {
        "$header\n\n$trimmed"
    }
}

fun validateGenerate(apiKey: String, photoCount: Int): String? = when {
    apiKey.isBlank() -> "Добавьте OpenAI ключ в Настройках или вставьте sk-demo."
    photoCount < 1 -> "Добавьте хотя бы одно фото товара."
    else -> null
}
